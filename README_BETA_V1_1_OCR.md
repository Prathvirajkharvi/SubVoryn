# SubVoryn — Beta V1.1 OCR

**Primary job:** import a video with burned-in English subtitles, scan frames locally with OCR, merge repeated dialogue observations, estimate cue end times, review the resulting cues, then export SRT, VTT, or ASS.

## What's different from Beta V1

- Added a dedicated **Import hardsub video** action with a video-only picker and a safe thumbnail/duration read.
- Kept subtitle-file import as an optional, separate workflow.
- Prevented video files from being read into a subtitle-text buffer; subtitle import only accepts `.srt`, `.vtt`, `.ass`, and `.ssa` files and is capped at 20 MB.
- Added local English OCR using Tesseract4Android 4.9.0 and the bundled English traineddata model.
- Added Fast (1200 ms sampling), Balanced (600 ms), and Accurate (300 ms) sampling modes.
- Added Bottom 35%, Bottom 50%, Top 35%, and Full frame OCR regions.
- Added progress, Cancel, partial-result export, duplicate cue merging, and SRT/VTT/ASS export.
- Frames with the same region fingerprint reuse the previous OCR text instead of running OCR again.

## How extraction works

1. Choose **Import hardsub video**. The app reads only video metadata and a preview frame; it does not read the entire video as text.
2. Choose a scan speed and subtitle area.
3. Press **Extract subtitles**. Frames are sampled in a background thread and an on-device English OCR model reads the selected region.
4. Observations with the same normalized text are merged across nearby samples. Cue boundaries are estimates derived from sample timestamps—not ground-truth subtitle timings.
5. Review short cues, correct timestamps if needed, choose SRT/VTT/ASS, and export.

## Important limitations

- This first OCR prototype uses **Tesseract4Android**, not PaddleOCR. PaddleOCR remains a future alternate engine; this implementation is a practical on-device English baseline.
- OCR accuracy depends on subtitle font, contrast, outline, resolution, motion, and selected scan region. It may recognize credits/signage or miss very brief text.
- With sampling at 600 ms, dialogue start/end timing has limited precision. Use Accurate (300 ms) for short dialogue, understanding that it may take a long time for a 40-minute video.
- `MediaMetadataRetriever` performance and supported codecs depend on the Android device. If a video fails to open, try a standard H.264 MP4.
- The project uses Android Gradle Plugin 8.10.1 / Java 17 and a remote Maven/JitPack OCR dependency. **Classic AIDE does not fully support modern Gradle projects**, so this source may need AndroidIDE (AGP 7.2+) or Android Studio to build. The current execution environment lacks an Android SDK and Gradle, so no APK was compiled or device-tested here.

## Build requirements

- Android Gradle Plugin 8.10.1
- Gradle 8.11.1 or compatible wrapper
- JDK 17
- Android SDK platform 35
- Network access for the first build to resolve `cz.adaptech.tesseract4android:tesseract4android:4.9.0` from JitPack

The English OCR model is bundled at `app/src/main/assets/tessdata/eng.traineddata`, so it is not downloaded when the app runs.

## Package

- Application ID: `com.subvoryn.voryn`
- Version: `0.2.0-beta1`
- Min SDK: 21
- Target SDK: 29 (Storage Access Framework is used for video and subtitle selection/export)
