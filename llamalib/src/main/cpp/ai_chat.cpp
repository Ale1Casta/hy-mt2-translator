#include <android/log.h>
#include <jni.h>
#include <atomic>
#include <iomanip>
#include <cmath>
#include <string>
#include <unistd.h>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 1;
constexpr int   N_THREADS_MAX           = 8;

// Thread scelti dal benchmark, cambiati da setThreads().
// n_threads serve alla scrittura (un token alla volta), n_threads_batch alla lettura del testo (prompt):
// llama.cpp li tiene separati, quindi il migliore per l'una non deve per forza valere per l'altra.
static std::atomic<int> g_n_threads{2};
static std::atomic<int> g_n_threads_batch{2};

static int clamp_threads(int n) {
    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, n));
}


constexpr int   DEFAULT_CONTEXT_SIZE    = 2048;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.7f;

static llama_model                      * g_model;
static llama_context                    * g_context;
static llama_batch                        g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler;

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    // Set llama log handler to Android
    llama_log_set(aichat_android_log_callback, nullptr);

    // Loading all CPU backend variants
    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Initialize backends
    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGd("%s: Loading model from: \n%s\n", __func__, model_path);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

static llama_context *init_context(llama_model *model, const int n_ctx = DEFAULT_CONTEXT_SIZE,
                                   int n_threads = 0, int n_threads_batch = 0) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    // Multi-threading setup: se non indicati, si usano i valori scelti dal benchmark
    n_threads = clamp_threads(n_threads > 0 ? n_threads : g_n_threads.load());
    n_threads_batch = clamp_threads(n_threads_batch > 0 ? n_threads_batch : g_n_threads_batch.load());
    LOGi("%s: Using %d threads (generation), %d threads (batch)", __func__, n_threads, n_threads_batch);

    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d context size...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads_batch;
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_new_context_with_model() returned null)", __func__);
    }
    return context;
}

static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_k = 20;
    sparams.top_p = 0.6f;
    sparams.penalty_repeat = 1.05f;
    return common_sampler_init(g_model, sparams);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    auto *context = init_context(g_model);
    if (!context) { return 1; }
    g_context = context;
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = new_sampler(DEFAULT_SAMPLER_TEMP);
    return 0;
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    auto *context = init_context(g_model, pp);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const uint32_t n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp = %d)", pp);

        common_batch_clear(g_batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(g_batch, 0, i, {0}, false);
        }

        g_batch.logits[g_batch.n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(context, g_batch) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg = %d)", tg);

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            common_batch_clear(g_batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(g_batch, 0, i, {j}, true);
            }

            if (llama_decode(context, g_batch) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Una prova di lettura (pp token in un solo batch) e di scrittura (tg token uno alla volta)
 * sul contesto indicato. Restituisce false se llama_decode fallisce.
 */
static bool bench_once(llama_context *context, int pp, int tg, double &speed_pp, double &speed_tg) {
    common_batch_clear(g_batch);
    for (int i = 0; i < pp; i++) {
        common_batch_add(g_batch, 0, i, {0}, false);
    }
    g_batch.logits[g_batch.n_tokens - 1] = true;
    llama_memory_clear(llama_get_memory(context), false);

    const auto t_pp_start = ggml_time_us();
    if (llama_decode(context, g_batch) != 0) { return false; }
    const auto t_pp_end = ggml_time_us();

    llama_memory_clear(llama_get_memory(context), false);
    const auto t_tg_start = ggml_time_us();
    for (int i = 0; i < tg; i++) {
        common_batch_clear(g_batch);
        common_batch_add(g_batch, 0, i, {0}, true);
        if (llama_decode(context, g_batch) != 0) { return false; }
    }
    const auto t_tg_end = ggml_time_us();
    llama_memory_clear(llama_get_memory(context), false);

    speed_pp = double(pp) / (double(t_pp_end - t_pp_start) / 1000000.0);
    speed_tg = double(tg) / (double(t_tg_end - t_tg_start) / 1000000.0);
    return true;
}

/**
 * Benchmark con un numero di thread preciso, senza toccare quelli in uso per la traduzione.
 * Fa una prova di riscaldamento (non misurata) e poi nr prove, e restituisce le singole misure
 * in token al secondo, così la mediana e la variabilità si calcolano in Kotlin:
 *   "pp=22.1,22.4,21.9;tg=8.5,8.4,8.6"
 * In caso di errore la stringa comincia con "error:".
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchWithThreads(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                                  jint nr, jint n_threads, jint n_threads_batch) {
    if (!g_model) {
        return env->NewStringUTF("error: model not loaded");
    }
    if (pp < 1 || pp > BATCH_SIZE || tg < 1 || tg > pp || nr < 1) {
        return env->NewStringUTF("error: invalid parameters");
    }
    auto *context = init_context(g_model, pp, n_threads, n_threads_batch);
    if (!context) {
        return env->NewStringUTF("error: init_context failed");
    }

    double speed_pp = 0.0;
    double speed_tg = 0.0;
    // Riscaldamento breve: carica pesi e cache, avvia i thread; il risultato si scarta
    if (!bench_once(context, pp, std::min(4, (int) tg), speed_pp, speed_tg)) {
        llama_free(context);
        return env->NewStringUTF("error: llama_decode failed (warm-up)");
    }

    std::vector<double> pps;
    std::vector<double> tgs;
    for (int r = 0; r < nr; r++) {
        if (!bench_once(context, pp, tg, speed_pp, speed_tg)) {
            llama_free(context);
            return env->NewStringUTF("error: llama_decode failed");
        }
        pps.push_back(speed_pp);
        tgs.push_back(speed_tg);
        LOGi("bench %d/%d threads: pp %f t/s, tg %f t/s", (int) n_threads_batch, (int) n_threads, speed_pp, speed_tg);
    }
    llama_free(context);

    std::stringstream result;
    result << std::fixed << std::setprecision(3);
    result << "pp=" << join(pps, ",") << ";tg=" << join(tgs, ",");
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_position;
static llama_pos current_position;

// Token del prompt attualmente presenti nella cache del modello (KV).
// Serve a riconoscere la parte iniziale del prompt già elaborata, per non rileggerla.
static llama_tokens cached_prompt_tokens;

static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;
    cached_prompt_tokens.clear();

    if (clear_kv_cache)
        llama_memory_clear(llama_get_memory(g_context), false);
}

/**
 * TODO-hyin: implement sliding-window version as a better alternative
 *
 * Context shifting by discarding the older half of the tokens appended after system prompt:
 * - take the [system_prompt_position] first tokens from the original prompt
 * - take half of the last (system_prompt_position - system_prompt_position) tokens
 * - recompute the logits in batches
 */
static void shift_context() {
    const int n_discard = (current_position - system_prompt_position) / 2;
    LOGi("%s: Discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_position + n_discard, current_position, -n_discard);
    current_position -= n_discard;
    LOGi("%s: Context shifting done! Current position: %d", __func__, current_position);
    // Dopo lo spostamento la cache non corrisponde più ai token salvati
    cached_prompt_tokens.clear();
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ true);
    chat_msgs.push_back(new_msg);
    LOGi("%s: Formatted and added %s message (%d bytes)", __func__, role.c_str(), (int) formatted.size());
    return formatted;
}

/**
 * Completion loop's short-term states:
 * - stop generation position
 * - token chars caching
 * - current assistant message being generated
 */
static llama_pos stop_generation_position;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

static int decode_tokens_in_batches(
        llama_context *context,
        llama_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    // Process tokens in batches using the global batch
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(), start_pos);
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur_batch_size = std::min((int) tokens.size() - i, BATCH_SIZE);
        common_batch_clear(batch);
        LOGv("%s: Preparing a batch size of %d starting at: %d", __func__, cur_batch_size, i);

        // Shift context if current batch cannot fit into the context
        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            LOGw("%s: Current batch won't fit into context! Shifting...", __func__);
            shift_context();
        }

        // Add tokens to the batch with proper positions
        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
            common_batch_add(batch, token_id, position, {0}, want_logit);
        }

        // Decode this batch
        const int decode_result = llama_decode(context, batch);
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Obtain system prompt from JEnv
    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    LOGd("%s: System prompt received (%d bytes)", __func__, (int) std::string(system_prompt).size());
    std::string formatted_system_prompt(system_prompt);

    // Format system prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // Tokenize system prompt
    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);
    for (auto id: system_tokens) {
        LOGv("token id: %d", id);
    }

    // Handle context overflow
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt too long for context! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    // Decode system tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    system_prompt_position = current_position = (int) system_tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();

    // Ogni richiesta è indipendente: azzera lo storico dei messaggi e il campionatore.
    // La cache del modello (KV) NON si azzera: si riusa la parte iniziale del prompt.
    chat_msgs.clear();
    system_prompt_position = 0;
    common_sampler_reset(g_sampler);

    // Obtain and tokenize user prompt
    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    LOGd("%s: User prompt received (%d bytes)", __func__, (int) std::string(user_prompt).size());
    std::string formatted_user_prompt(user_prompt);

    // Format user prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);
    }
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Decode formatted user prompts
    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);
    for (auto id: user_tokens) {
        LOGv("token id: %d", id);
    }

    // Il prompt deve stare nel contesto: se è troppo lungo, si tronca
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if ((int) user_tokens.size() > max_batch_size) {
        const int skipped_tokens = (int) user_tokens.size() - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Riuso del prefisso: quanti token iniziali coincidono con quelli già nella cache?
    size_t common = 0;
    const size_t max_common = std::min(cached_prompt_tokens.size(), user_tokens.size());
    while (common < max_common && cached_prompt_tokens[common] == user_tokens[common]) {
        common++;
    }
    // Serve almeno un token da elaborare, per ottenere i logit dell'ultimo
    if (common == user_tokens.size() && common > 0) {
        common--;
    }

    // Toglie dalla cache tutto ciò che viene dopo il prefisso comune (anche l'ultima risposta)
    if (!llama_memory_seq_rm(llama_get_memory(g_context), 0, (llama_pos) common, -1)) {
        // Rimozione parziale non riuscita: si riparte da zero
        llama_memory_clear(llama_get_memory(g_context), false);
        common = 0;
    }
    LOGi("%s: Riuso %d token su %d", __func__, (int) common, (int) user_tokens.size());

    // Elabora solo i token nuovi
    const llama_tokens new_tokens(user_tokens.begin() + common, user_tokens.end());
    if (decode_tokens_in_batches(g_context, g_batch, new_tokens, (llama_pos) common, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        cached_prompt_tokens.clear();
        return 2;
    }

    // Aggiorna lo stato
    cached_prompt_tokens = user_tokens;
    current_position = (llama_pos) user_tokens.size();
    stop_generation_position = current_position + n_predict;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_tokenCount(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jtext
) {
    // Conta i token di un testo semplice (serve a dimensionare il prompt della chat)
    const auto *text = env->GetStringUTFChars(jtext, nullptr);
    const auto tokens = common_tokenize(g_context, std::string(text), false, false);
    env->ReleaseStringUTFChars(jtext, text);
    return (jint) tokens.size();
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    // Infinite text generation via context shifting
    if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Stop if reaching the marked position
    if (current_position >= stop_generation_position) {
        LOGw("%s: STOP: hitting stop position: %d", __func__, stop_generation_position);
        return nullptr;
    }

    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    // Populate the batch with new token, then decode
    common_batch_clear(g_batch);
    common_batch_add(g_batch, new_token_id, current_position, {0}, true);
    if (llama_decode(g_context, g_batch) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return nullptr;
    }

    // Update position
    current_position++;

    // Stop if next token is EOG
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("id: %d,\tIS EOG!\nSTOP.", new_token_id);
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    // If not EOG, convert to text
    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    // Create and return a valid UTF-8 Java string
    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        LOGv("token id: %d", new_token_id);

        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        LOGv("id: %d,\tappend to cache", new_token_id);
        result = env->NewStringUTF("");
    }
    return result;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Free up resources
    common_sampler_free(g_sampler);
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    llama_free(g_context);
    g_context = nullptr;
    llama_model_free(g_model);
    g_model = nullptr;
}

/**
 * Cambia i thread: n per la scrittura, n_batch per la lettura del testo.
 * Se il modello è già caricato vale subito, senza ricaricarlo (llama_set_n_threads);
 * altrimenti vale al prossimo caricamento. Va chiamata dallo stesso thread del motore.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_setThreads(JNIEnv *, jobject /*unused*/, jint n, jint n_batch) {
    const int threads = clamp_threads((int) n);
    const int threads_batch = clamp_threads((int) n_batch);
    g_n_threads.store(threads);
    g_n_threads_batch.store(threads_batch);
    if (g_context) {
        llama_set_n_threads(g_context, threads, threads_batch);
    }
    LOGi("%s: threads set to %d (generation), %d (batch)", __func__, threads, threads_batch);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}