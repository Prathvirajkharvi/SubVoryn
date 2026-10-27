# SubVoryn FFmpeg Engine — dependency and capability notes

SubVoryn pins `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9`, a maintained Android continuation of FFmpegKit. The API remains under `com.arthenica.ffmpegkit` for compatibility with the upstream API. Official project: https://github.com/ffmpegkit-maintained/ffmpeg

## Compatibility

- Android `minSdk` 24 is required by the pinned 8.1 line.
- Published native ABIs for this release line are `arm64-v8a` and `x86_64`; 32-bit-only Android devices are not supported by this bundled engine.
- The `full` (non-GPL) package is licensed under LGPL-3.0 and includes the `subtitles`/libass filter and font support. The application verifies the actual runtime filter list before encoding and checks a usable H.264 encoder with a one-frame probe.
- The non-GPL `full` package does **not** include GPL-only `libx264`. When that encoder is unavailable, the app probes available encoders such as Android MediaCodec and uses a bitrate target rather than falsely claiming that CRF or x264 presets apply. Exact codec support is device/build dependent.
- Native runtime updates are delivered through a reviewed SubVoryn app release. The app checks Maven Central metadata on request but does not download and execute a native binary at runtime. Thus there is no fake download progress or claim of an automatic in-app binary installer.

## License and notices

The selected FFmpeg package is LGPL-3.0. The complete LGPL-3.0 text is bundled at `app/src/main/assets/third_party/FFmpeg-LGPL-3.0.txt` and can be viewed in Settings. Third-party codec/library licenses may differ; consult the upstream package's third-party notices and the exact pinned AAR before distributing a release. Official pinned release: https://github.com/ffmpegkit-maintained/ffmpeg/releases/tag/v8.1.9-lts-android

This note is not legal advice. Confirm all bundled component notices and LGPL distribution obligations for the intended distribution channel.
