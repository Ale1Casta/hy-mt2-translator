# Hy-MT2 Translator

**Traduzione e trascrizione vocale offline su Android: tutto gira sul telefono.**

🇬🇧 [Read in English](README.md)

Hy-MT2 Translator è un progetto nato per imparare. È un'app Android che traduce testo in 39 lingue con **Tencent Hy-MT2-1.8B** e trascrive il parlato con **Qwen3-ASR 0.6B**. Entrambi i modelli girano in locale sulla CPU del telefono, tramite llama.cpp e sherpa-onnx. Non ci sono server, account o connessione a internet: testi e audio non lasciano mai il dispositivo.

<!-- Screenshot: aggiungere le immagini in docs/screenshots/ e togliere il commento
<p align="center">
  <img src="docs/screenshots/translate.png" width="230">
  <img src="docs/screenshots/chat.png" width="230">
  <img src="docs/screenshots/settings.png" width="230">
</p>
-->

---

## Funzionalità

### Traduci
- Testo libero → traduzione in una delle **39 lingue di destinazione** (tutta la tabella di Hy-MT2, compresi cantonese, cinese tradizionale, tibetano e uiguro).
- **4 toni**, dal meno al più formale: *Informale*, *Neutro* (predefinito), *Formale*, *Tecnico*. Ogni tono diventa un'istruzione di stile nel formato di prompt ufficiale di Hy-MT2.
- La traduzione **compare mentre viene generata**. Si può fermare, copiare o condividere.
- Statistiche di ogni traduzione: tempo al primo token, velocità di lettura e di scrittura.
- L'ultima lingua di destinazione e l'ultimo tono vengono ricordati.

### Chat di traduzione
- Una sezione separata per le **conversazioni continue**. Il contesto viene mantenuto e applicato a ogni nuova traduzione.
- Impostazioni fisse per ogni chat: **lingua di destinazione, stile, glossario** (termine → traduzione obbligata), **contesto** di sfondo e **istruzioni** salvate.
- **Messaggi istruzione**: scorrendo verso l'alto sul pulsante di invio, il messaggio viene mandato al modello come istruzione invece che come testo da tradurre.
- **Finestra scorrevole**: quando il prompt raggiunge il suo budget (~800 token), gli scambi più vecchi vengono dimenticati e le impostazioni fisse restano. Il prompt è ordinato in modo da riusare la cache del modello.
- Più chat salvate, **temporanee per impostazione predefinita**: ogni chat si elimina dopo N giorni senza usarla (1, 3, 7, 14 o 30; predefinito 7). Le card vicine alla scadenza vengono evidenziate.
- **Esportazione** in **PDF, TXT, CSV** (originale | traduzione, UTF-8 con BOM per Excel) e **Markdown**, con o senza originali e impostazioni.
- Le chat sono salvate in JSON nella memoria privata dell'app: nessun'altra app può leggerle e spariscono quando si disinstalla l'app.

### Trascrizione vocale
- **Microfono** in entrambe le sezioni: un tocco per registrare (fino a 2 minuti), un altro per trascrivere. La lingua parlata viene **riconosciuta in automatico**.
- **Condivisione da altre app**: note vocali e video (per esempio da WhatsApp) si possono inviare all'app dal menu Condividi di Android (`audio/*`, `video/*`).
- L'audio viene diviso nelle pause con **Silero VAD** in pezzi di al massimo ~18 s, e ogni pezzo viene trascritto da solo.
- La trascrizione si può modificare e poi tradurre con un tocco.
- Il modello di trascrizione viene **scaricato dalla RAM dopo 2 minuti** senza trascrizioni.

### Benchmark sull'hardware
- Al primo avvio l'app fa un breve **benchmark** di entrambi i modelli. Legge la topologia della CPU (`cpu_capacity`, frequenza massima) e misura diversi numeri di thread.
- Per Hy-MT2 si misurano separatamente la lettura del prompt (pp32) e la scrittura (tg16): mediana di più prove, con riscaldamento e ordine alternato.
- La configurazione più veloce viene **scelta in automatico** e segnata con ★ nella tabella delle Impostazioni. Il benchmark si può rilanciare in qualsiasi momento.

### Interfaccia
- Jetpack Compose, stile **glass** con accento arancione, **tema giorno/notte** che segue il sistema oppure si imposta a mano.
- Tab **Traduci | Chat** con scorrimento laterale. Una linguetta sul bordo apre un pannello rapido con Impostazioni e il pulsante giorno/notte (un tocco alterna, un tocco lungo torna all'automatico).

---

## Modelli

| | Traduzione | Trascrizione | Rilevamento voce |
|---|---|---|---|
| **Modello** | [Tencent Hy-MT2-1.8B](https://huggingface.co/tencent/Hy-MT2-1.8B) | [Qwen3-ASR-0.6B](https://huggingface.co/Qwen/Qwen3-ASR-0.6B) | [Silero VAD](https://github.com/snakers4/silero-vad) |
| **Formato usato** | GGUF `Q4_K_M` (~1,1 GB) | ONNX int8, export sherpa-onnx (~940 MB) | ONNX (~0,6 MB) |
| **Runtime** | llama.cpp (JNI) | sherpa-onnx + ONNX Runtime | sherpa-onnx |
| **Licenza** | Apache 2.0 | Apache 2.0 | MIT |

### Hy-MT2-1.8B (Tencent Hunyuan)
Modello di traduzione multilingue "a pensiero veloce", pubblicato a maggio 2026 in tre taglie (1.8B, 7B, 30B-A3B MoE). Traduce tra 33 lingue più alcune lingue minoritarie e dialetti cinesi, e segue le istruzioni di traduzione: stile, terminologia, contesto, formattazione. La versione 1.8B è abbastanza piccola per un telefono. L'app usa i template di prompt ufficiali e i parametri consigliati: `temperature 0.7`, `top_k 20`, `top_p 0.6`, `repetition_penalty 1.05`, con un contesto di 2048 token.

> Il supporto di Hy-MT2 in llama.cpp arriva dalla **PR [#22836](https://github.com/ggml-org/llama.cpp/pull/22836)** (kernel STQ di Tencent). Per questo llama.cpp è incluso come submodule fissato al commit `1e411d8f5a` di quella PR.

### Qwen3-ASR-0.6B (Alibaba Qwen)
Modello compatto di riconoscimento vocale "tutto in uno": riconosce la lingua e trascrive 30 lingue e 22 dialetti cinesi. Gira tramite l'export ONNX int8 fornito da sherpa-onnx (`sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25`). L'app lo legge **direttamente dall'APK**, senza copiarlo.

### Prestazioni misurate
Hy-MT2 `Q4_K_M` su un telefono con **Snapdragon 695** (6 GB di RAM): ~25–30 tok/s in lettura e ~10 tok/s in scrittura con 2 thread. Con più thread questo chip andava più lento, ed è per questo che l'app misura ogni dispositivo invece di dare per scontata la configurazione. L'app è stata provata anche su uno **Snapdragon 660** (4 GB di RAM).

---

## Installazione (APK)

L'app non è sul Play Store: si distribuisce come APK, per uso personale.

- **Requisiti:** Android 10 o successivo (API 29), **arm64-v8a**, circa **2,2 GB** per l'APK più **~1,1 GB** di spazio libero al primo avvio, RAM consigliata ≥ 4 GB.
- Al primo avvio il modello di traduzione viene copiato dall'APK nella memoria interna, perché llama.cpp ha bisogno di un file vero. Compare una barra di avanzamento e la copia si fa una volta sola.
- L'APK pesa circa **2,05 GB**, quindi **supera il limite di 2 GiB** per un file di una Release di GitHub. Link per il download: *(inserire qui il link)*.

L'APK release è firmato con una chiave di debug: va bene per installarlo tra amici, ma non è pensato per la pubblicazione su uno store.

---

## Compilare dal codice sorgente

Prerequisiti: **Android Studio** (AGP 9.5), **NDK 29.0.13113456**, **CMake 3.31.6**, JDK 11+.

```bash
git clone --recurse-submodules https://github.com/<tuo-username>/hy-mt2-translator.git
```

Se il clone è già stato fatto senza submodule: `git submodule update --init`.

I modelli **non sono nella repository** perché troppo grandi. Vanno scaricati e messi in `app/src/release/assets/models/`:

```
app/src/release/assets/models/
├── Hy-MT2-1.8B-Q4_K_M.gguf          ← huggingface.co/tencent/Hy-MT2-1.8B-GGUF
└── asr/                              ← release "asr-models" di sherpa-onnx:
    ├── conv_frontend.onnx               sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25
    ├── encoder.int8.onnx
    ├── decoder.int8.onnx
    ├── tokenizer/  (merges.txt, tokenizer_config.json, vocab.json)
    └── silero_vad.onnx              ← release "asr-models" di sherpa-onnx: silero_vad.onnx
```

- Build **release** (`assembleRelease`): i modelli finiscono nell'APK senza compressione (`noCompress += gguf, onnx`).
- Build **debug**: l'APK non contiene i modelli. Il `.gguf` va copiato con adb nella cartella esterna dell'app:
  ```bash
  adb push Hy-MT2-1.8B-Q4_K_M.gguf /sdcard/Android/data/com.example.hy_mt2translator/files/
  ```

Le librerie native precompilate di sherpa-onnx (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) sono in `app/src/main/jniLibs/arm64-v8a/`.

---

## Struttura del progetto

```
app/                    app Kotlin / Jetpack Compose
  ├─ TranslatorScreen      sezione Traduci
  ├─ Chat*                 chat: schermata, impostazioni, prompt, archivio, esportazione
  ├─ AsrEngine, Recorder,  trascrizione: Qwen3-ASR + Silero VAD,
  │  AudioDecoder            registrazione e decodifica di audio/video condivisi
  ├─ Benchmark*, CpuTopology  benchmark e scelta dei thread
  ├─ ModelManager          estrazione e caricamento del modello
  └─ ui/theme              tema glass, icone, colori
llamalib/               ponte JNI verso llama.cpp (adattato dall'esempio Android di llama.cpp)
third_party/llama.cpp   submodule, PR #22836
```

---

## Limiti noti
- Solo arm64 e solo CPU (niente GPU/NPU).
- Interfaccia solo in italiano.
- Sono stati osservati alcuni errori di traduzione ancora da indagare, per esempio nomi di colori omessi o tradotti male negli elenchi.
- Nel benchmark la composizione dei thread sui core è quella *richiesta*, non garantita, perché l'affinità dei thread non è controllata.

## Sviluppi futuri
- Provare la quantizzazione a 1,25 bit di Hy-MT2 (~440 MB).
- Scelta del modello dentro l'app, per provare modelli di traduzione alternativi.
- Indagine sugli errori di traduzione (temperatura 0 contro parametri consigliati).

---

## Crediti e licenze

| Componente | Licenza |
|---|---|
| [Hy-MT2](https://github.com/Tencent-Hunyuan/Hy-MT2), Tencent Hunyuan | Apache 2.0 |
| [Qwen3-ASR](https://github.com/QwenLM/Qwen3-ASR), Alibaba Qwen | Apache 2.0 |
| [llama.cpp](https://github.com/ggml-org/llama.cpp), ggml-org | MIT |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), k2-fsa (librerie precompilate e API Kotlin in `com.k2fsa.sherpa.onnx`) | Apache 2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime), Microsoft | MIT |
| [Silero VAD](https://github.com/snakers4/silero-vad) | MIT |

Il codice dell'app è rilasciato con la licenza indicata in [LICENSE](LICENSE). I modelli **non** sono coperti da quella licenza e restano soggetti alle proprie.

Progetto indipendente, non commerciale e a scopo didattico. Non è affiliato a Tencent, ad Alibaba né agli autori delle librerie citate.
