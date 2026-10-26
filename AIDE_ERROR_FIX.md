# SubVoryn Beta V1.1 — Dependency Resolution Fix

The previous package used the custom JitPack coordinate from the upstream README:

```gradle
implementation 'cz.adaptech.tesseract4android:tesseract4android:4.9.0'
```

An upstream issue documented cases where the custom-coordinate artifact path returned a missing artifact, while the standard JitPack repository coordinate worked. This package therefore uses:

```gradle
implementation 'com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.9.0'
```

The package remains hosted on JitPack. `settings.gradle` must include:

```gradle
maven { url = uri("https://jitpack.io") }
```

## Important

- Do not delete `import com.googlecode.tesseract.android.TessBaseAPI;` from `MainActivity.java`; the class is supplied by Tesseract4Android.
- Do not change `main.xml` for this dependency error.
- This project uses Android Gradle Plugin 8.10.1 and Java 17. Classic AIDE versions may not fully support this Gradle setup or download external AAR dependencies. If AIDE still shows unresolved classes, build the project using the included GitHub Actions workflow instead of compiling it directly in AIDE.
- The workflow has not been executed in this environment, and APK/device behavior is not claimed as verified.
