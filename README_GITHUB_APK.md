# SubVoryn Beta V1.1 — Build APK from GitHub Actions

## Important: do not build this project with classic AIDE's Maven dialog

This project uses Android Gradle Plugin 8.10.1, Gradle 8.11.1, JDK 17, and Tesseract4Android 4.9.0 from JitPack. Classic AIDE's Java editor can keep showing `TessBaseAPI` as unresolved and its Maven-library downloader may not resolve the dependency. The source itself does not include a compiled APK.

## Build from a phone using GitHub Actions

1. Extract the ZIP on your phone. Open the `SubVoryn` folder.
2. Create a new GitHub repository.
3. Upload the contents INSIDE `SubVoryn` into the repository root. Do not upload only the outer `SubVoryn` folder. Keep `.github/workflows/android-build.yml`.
4. Open the repository's **Actions** tab. Choose **Build SubVoryn APK** and press **Run workflow**.
5. When the run finishes successfully, open that run and download the `SubVoryn-Beta-V1.1-debug-apk` artifact.
6. Extract the artifact ZIP and install `app-debug.apk`.

If the workflow fails, open the run, select the failed job and share the final 30–50 lines of its log.

## Current scope

The source includes video selection, OCR scan controls, the English Tesseract traineddata asset, subtitle cue editing, duplicate cleanup, and SRT/VTT/ASS export. It is an unverified source package until the workflow completes and the app is tested on a device.
