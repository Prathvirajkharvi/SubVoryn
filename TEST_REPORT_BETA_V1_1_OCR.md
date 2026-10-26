# SubVoryn Beta V1.1 OCR — Validation report

## Source checks completed

- Reviewed the Beta V1 import path: it used a universal `*/*` picker and read the chosen bytes as UTF-8 text. Choosing a large MP4/MKV there could allocate a huge byte buffer and crash. The new source has a separate `video/*` picker and validates subtitle extensions before reading.
- The video workflow reads duration and one preview frame through `MediaMetadataRetriever`, then samples frames in a background thread.
- The English traineddata asset is present and is approximately 4 MB.
- The conversion engine remains the existing Java implementation, with SRT/VTT/ASS import/export and timing review.

## Not verified in this environment

- Android Gradle build / APK assembly: not run (no Android SDK or Gradle executable available in the build container).
- Tesseract4Android dependency resolution: not run (requires Gradle/Maven network access).
- Video OCR speed and recognition accuracy: not tested on the user's Infinix device.
- Video codec support: depends on the Android media stack and input format.

This is source-level validation, not a claim that an APK has been built or that on-device OCR has been tested.
