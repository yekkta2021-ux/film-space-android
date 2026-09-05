# Film Space Android — unofficial experimental preview 0.1.0

An independent Android / ARCore virtual filmmaking studio inspired by **[Film Space](https://github.com/maxprokopp/film-space)**, the original iPhone / ARKit project created by **[Max Prokopp (@maxprokopp)](https://github.com/maxprokopp)**.

Max's original project introduced the workflow that inspired this preview: arrange a virtual scene, perform camera movements with a phone, and record a reference clip for later video work. Please visit and support the original Film Space repository for the iOS application and its creator's work.

This Android implementation was prepared with AI coding assistance for [@yekkta2021-ux](https://github.com/yekkta2021-ux). It is experimental, independently maintained, and is not an official release or an endorsement by Max Prokopp. The original Swift source, logos, screenshots and artwork are not included. See [ATTRIBUTION.md](ATTRIBUTION.md) for credit and licensing status.

## Download

[Download the experimental Android APK](downloads/FilmSpace-Android-S21FE.apk) · [Persian installation guide](INSTALL-fa.txt) · [SHA-256 checksum](downloads/SHA256.txt)

The APK has passed build and signature checks, but has **not been tested on a physical Android device or emulator**. Please test a short recording before relying on it.

## Device and features

- Delivered APK: arm64-v8a, Android 10 or later; intended for the user's Samsung Galaxy S21 FE.
- Native OpenGL ES virtual studio, 40 m checker grid, articulated human stand-ins.
- Tap to select, drag to arrange, rotate, add/delete up to 24 actors. Scene persists locally.
- Orbit, pinch zoom, manual camera translation/height, lock/return viewpoint.
- 35 / 50 / 75 / 200 mm lenses with a 36 mm horizontal sensor model and 16:9 framing.
- ARCore physical camera tracking, recenter and optional AR service installation. Edit mode does not require AR.
- H.264 MP4 recording at 1280×720 / nominal 30 fps, optional AAC microphone audio. Renders the scene directly into the encoder without toolbar overlays, saved via MediaStore to Movies/FilmSpace.
- Landscape UI; Persian in-app guide and English controls.

## Build

Open this directory in Android Studio with JDK 17, SDK 35 and Android Gradle Plugin 8.9.0. Sync and run `gradle :app:assembleDebug` using Gradle 8.11.1. The supplied APK is a separately signed personal build. A debug build has a different signing identity and cannot update it without uninstalling first.

The delivered APK was built locally using AAPT2 8.9.0-12782657, Java 17, D8 8.9.35 and apksig 8.9.0 because the complete SDK download was unavailable in the build environment. The ARCore manifest and resources were included explicitly. Signing material is private and is excluded from this archive.

## Validation and limitations

Compilation, DEX conversion, APK signature verification, manifest/architecture inspection, stored resource alignment and geometric mesh integrity checks passed. No phone or Android emulator was available for runtime testing. Camera tracking, GPU rendering, microphone synchronization, pause/resume and video playback still require a device test. This is a first experimental build, not a production-certified or feature-identical port. AR tracking can pause in dark or featureless environments; the image then holds its last tracked position. AR relocalization after interruptions is not guaranteed; use Center to reset.

## Third-party components

ARCore Android SDK 1.48.0, copyright Google LLC, is included in the APK under the ARCore Additional Terms of Service: https://developers.google.com/ar/develop/terms . AR features use Google Play Services for AR, governed by Google's privacy policy: https://policies.google.com/privacy . ARCore installation strings and native libraries are part of that SDK. The app has no advertising, analytics, account, backend, or Internet permission. Camera images are used by the AR service for tracking; recordings contain the rendered virtual scene.

## Device acceptance check

1. Install, open in landscape, move/select/rotate an actor and pinch to zoom.
2. Exit and reopen; confirm the actor arrangement was retained.
3. Enter Camera, grant camera permission and install/update Google Play Services for AR if prompted. Move slowly in a bright, textured environment; press Center and check physical translations.
4. Record 5–10 seconds with microphone permission, stop, open Video and check image, sound and duration.
5. Repeat without microphone permission; a silent video should save.
6. Test background/foreground during recording and AR, then Lock/Return. These tests have not yet been performed on the target phone.
