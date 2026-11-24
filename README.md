# Hy-MT2 Translator

**Offline translation and speech transcription on Android: everything runs on the phone.**

🇮🇹 [Leggi in italiano](README.it.md)

Hy-MT2 Translator is a learning project. It is an Android app that translates text into 39 languages with **Tencent Hy-MT2-1.8B** and transcribes speech with **Qwen3-ASR 0.6B**. Both models run locally on the phone's CPU, through llama.cpp and sherpa-onnx. There is no server, no account and no internet connection, so text and audio never leave the device.

> The app's interface is currently in **Italian only**.

<!-- Screenshots: add images in docs/screenshots/ and uncomment
<p align="center">
  <img src="docs/screenshots/translate.png" width="230">
  <img src="docs/screenshots/chat.png" width="230">
  <img src="docs/screenshots/settings.png" width="230">
</p>
-->

---

## Features

### Translate
- Free text → translation into one of **39 target languages** (the full Hy-MT2 table, including Cantonese, Traditional Chinese, Tibetan and Uyghur).
- **4 tones**, from least to most formal: *Informal*, *Neutral* (default), *Formal*, *Technical*. Each tone becomes a style instruction in Hy-MT2's official prompt format.
- The translation **streams** while it is being generated. You can stop it, copy it or share it.
- Statistics for every translation: time to first token, prompt and generation speed.
- The last target language and tone are remembered.

### Translation chat
- A separate section for **ongoing conversations**. The context is kept and applied to every new translation.
- Fixed settings for each chat: **target language, style, glossary** (term → required translation), **background context** and saved **instructions**.
- **Instruction messages**: swipe up on the send button to send a message as an instruction to the model instead of as text to translate.
- **Sliding window**: when the prompt reaches its token budget (~800 tokens), the oldest exchanges are dropped and the fixed settings are kept. The prompt is ordered so the model's KV cache can be reused.
- Several saved chats, **temporary by default**: each chat is deleted after N days without use (1, 3, 7, 14 or 30; default 7). Cards close to expiry are highlighted.
- **Export** to **PDF, TXT, CSV** (original | translation, UTF-8 with BOM for Excel) and **Markdown**, with or without originals and settings.
- Chats are stored as JSON in the app's private storage. No other app can read them, and they are removed when the app is uninstalled.

### Speech transcription
- **Microphone** in both sections: tap to record (up to 2 minutes), tap again to transcribe. The spoken language is **detected automatically**.
- **Share from other apps**: voice notes and videos (for example from WhatsApp) can be sent to the app through Android's share menu (`audio/*`, `video/*`).
- The audio is split at pauses with **Silero VAD** into segments of up to ~18 s, and each segment is transcribed separately.
- The transcription can be edited and then translated with one tap.
- The ASR model is **unloaded from RAM after 2 minutes** without transcriptions.

### Hardware-aware benchmark
- On first launch the app runs a short **benchmark** of both models. It reads the CPU topology (`cpu_capacity`, max frequency) and measures several thread counts.
- Hy-MT2 is measured separately for prompt reading (pp32) and generation (tg16): median of several runs, with warm-up and alternating order.
- The fastest configuration is **selected automatically** and marked with ★ in the Settings table. The benchmark can be run again at any time.

### Interface
- Jetpack Compose, **glass** style with an orange accent, **light/dark theme** that follows the system or can be set manually.
- **Translate | Chat** tabs with horizontal swipe. A side tab opens a quick panel with Settings and a day/night toggle (tap to switch, long press to return to automatic).

---

## Models

| | Translation | Transcription | Voice activity detection |
|---|---|---|---|
| **Model** | [Tencent Hy-MT2-1.8B](https://huggingface.co/tencent/Hy-MT2-1.8B) | [Qwen3-ASR-0.6B](https://huggingface.co/Qwen/Qwen3-ASR-0.6B) | [Silero VAD](https://github.com/snakers4/silero-vad) |
| **Format used** | GGUF `Q4_K_M` (~1.1 GB) | ONNX int8, sherpa-onnx export (~940 MB) | ONNX (~0.6 MB) |
| **Runtime** | llama.cpp (JNI) | sherpa-onnx + ONNX Runtime | sherpa-onnx |
| **License** | Apache 2.0 | Apache 2.0 | MIT |

### Hy-MT2-1.8B (Tencent Hunyuan)
A "fast-thinking" multilingual translation model released in May 2026 in three sizes (1.8B, 7B, 30B-A3B MoE). It translates between 33 languages plus several Chinese minority languages and dialects, and it follows translation instructions: style, terminology, context and formatting. The 1.8B version is small enough for a phone. The app uses the official prompt templates and the recommended sampling parameters: `temperature 0.7`, `top_k 20`, `top_p 0.6`, `repetition_penalty 1.05`, with a 2048-token context.

> Hy-MT2 support in llama.cpp comes from **PR [#22836](https://github.com/ggml-org/llama.cpp/pull/22836)** (Tencent's STQ kernels). For this reason llama.cpp is pinned to commit `1e411d8f5a` of that PR as a submodule.

### Qwen3-ASR-0.6B (Alibaba Qwen)
A compact all-in-one speech recognition model. It identifies the language and transcribes 30 languages and 22 Chinese dialects. It runs through the int8 ONNX export provided by sherpa-onnx (`sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25`). The app reads it **directly from the APK**, without copying it.

### Measured performance
Hy-MT2 `Q4_K_M` on a **Snapdragon 695** phone (6 GB RAM): ~25–30 tok/s for prompt reading and ~10 tok/s for generation with 2 threads. More threads were slower on this chip, which is why the app measures each device instead of assuming. The app has also been tested on a **Snapdragon 660** (4 GB RAM).

---

## Installation (APK)

The app is not on the Play Store. It is distributed as an APK for personal use.

- **Requirements:** Android 10 or later (API 29), **arm64-v8a**, about **2.2 GB** for the APK plus **~1.1 GB** of free space on first launch, recommended RAM ≥ 4 GB.
- On first launch the translation model is copied from the APK into internal storage, because llama.cpp needs a real file. A progress bar is shown and the copy happens only once.
- The APK is about **2.05 GB**, so it is **larger than the 2 GiB limit** for a GitHub Release asset. Download link: *(add link here)*.

The release APK is signed with a debug key. It is fine for sideloading between friends, but it is not meant for store publication.

---

## Building from source

Prerequisites: **Android Studio** (AGP 9.5), **NDK 29.0.13113456**, **CMake 3.31.6**, JDK 11+.

```bash
git clone --recurse-submodules https://github.com/<your-username>/hy-mt2-translator.git
```

If the clone has already been done without submodules: `git submodule update --init`.

The models are **not in the repository** because of their size. Download them and put them in `app/src/release/assets/models/`:

```
app/src/release/assets/models/
├── Hy-MT2-1.8B-Q4_K_M.gguf          ← huggingface.co/tencent/Hy-MT2-1.8B-GGUF
└── asr/                              ← sherpa-onnx release "asr-models":
    ├── conv_frontend.onnx               sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25
    ├── encoder.int8.onnx
    ├── decoder.int8.onnx
    ├── tokenizer/  (merges.txt, tokenizer_config.json, vocab.json)
    └── silero_vad.onnx              ← sherpa-onnx release "asr-models": silero_vad.onnx
```

- **Release** build (`assembleRelease`): the models are packed uncompressed into the APK (`noCompress += gguf, onnx`).
- **Debug** build: the APK contains no models. Copy the `.gguf` file with adb into the app's external folder:
  ```bash
  adb push Hy-MT2-1.8B-Q4_K_M.gguf /sdcard/Android/data/com.example.hy_mt2translator/files/
  ```

The prebuilt sherpa-onnx native libraries (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) are in `app/src/main/jniLibs/arm64-v8a/`.

---

## Project structure

```
app/                    Kotlin / Jetpack Compose app
  ├─ TranslatorScreen      Translate section
  ├─ Chat*                 chat: screen, settings, prompt, storage, export
  ├─ AsrEngine, Recorder,  transcription: Qwen3-ASR + Silero VAD,
  │  AudioDecoder            recording and decoding of shared audio/video
  ├─ Benchmark*, CpuTopology  benchmark and thread selection
  ├─ ModelManager          model extraction and loading
  └─ ui/theme              glass theme, icons, colors
llamalib/               JNI bridge to llama.cpp (adapted from llama.cpp's Android example)
third_party/llama.cpp   submodule, PR #22836
```

---

## Known limitations
- arm64 only, CPU only (no GPU/NPU).
- Interface in Italian only.
- Some translation errors have been observed and still need investigating, for example colour names that are dropped or mistranslated in lists.
- The thread-to-core composition in the benchmark is *requested*, not guaranteed, because thread affinity is not controlled.

## Roadmap
- Test the 1.25-bit Hy-MT2 quantization (~440 MB).
- In-app model selection, to try alternative translation models.
- Investigate translation errors (temperature 0 vs. recommended parameters).

---

## Credits and licenses

| Component | License |
|---|---|
| [Hy-MT2](https://github.com/Tencent-Hunyuan/Hy-MT2), Tencent Hunyuan | Apache 2.0 |
| [Qwen3-ASR](https://github.com/QwenLM/Qwen3-ASR), Alibaba Qwen | Apache 2.0 |
| [llama.cpp](https://github.com/ggml-org/llama.cpp), ggml-org | MIT |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), k2-fsa (prebuilt libraries and Kotlin API in `com.k2fsa.sherpa.onnx`) | Apache 2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime), Microsoft | MIT |
| [Silero VAD](https://github.com/snakers4/silero-vad) | MIT |

The app's own source code is released under the license in [LICENSE](LICENSE). The models are **not** covered by that license and are subject to their own licenses.

This is an independent, non-commercial project for learning purposes. It is not affiliated with Tencent, Alibaba or the authors of the libraries above.
