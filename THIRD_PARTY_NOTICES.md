# Third-party notices

## Maintained FFmpegKit for Android

SubVoryn pins `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9`, a maintained Android FFmpegKit continuation under LGPL-3.0. The `full` variant includes libass/subtitles, font support, and MediaCodec; it does not bundle the GPL-only libx264 encoder. At runtime the app enumerates filters/encoders and probes an H.264 encoder before encoding. 64-bit Android ABI support is `arm64-v8a` and `x86_64`; minSdk 24 is required. License text is bundled at `app/src/main/assets/third_party/FFmpeg-LGPL-3.0.txt`. Upstream: https://github.com/ffmpegkit-maintained/ffmpeg

## Tesseract4Android

This project references Tesseract4Android (`cz.adaptech.tesseract4android:tesseract4android:4.9.0`), which is an open-source Android wrapper around Tesseract OCR and related image libraries. See https://github.com/adaptech-cz/Tesseract4Android for its license and dependencies.

## English OCR trained data

The bundled `eng.traineddata` comes from the Tesseract `tessdata_fast` English model distributed under Apache License 2.0. Source: https://github.com/tesseract-ocr/tessdata_fast/ .

Copyright notices and full license terms are included in the upstream projects. No video is uploaded by this application; OCR is intended to run on-device.
