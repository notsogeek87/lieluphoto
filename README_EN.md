# LieluPhoto

[Français](./README.md)

LieluPhoto is a photo-focused Android camera app that brings back the handling and image quality of a modern mirrorless digital camera.

> **Fork of [PhotonCamera](https://github.com/bjzhou/photoncamera)** by bjzhou, released under the Apache License 2.0. The foundation (rendering engine, LUTs, color recipes, multi-frame stacking, and more) is their work, and this repository follows its evolution and adds its own changes.

## What differs from PhotonCamera

* **Identity**: app renamed LieluPhoto (`eu.lielu.photo`), Bugly removed.
* **Modes**: Photo · Portrait · Video, plus a **Night** switch that works with both Photo and Portrait.
* **Portrait**: AI bokeh with an aperture dial (ƒ/1 to ƒ/16) next to the zoom. The depth model is downloaded the first time Portrait is used.
* **AI enhancement**: noise cleanup (NAFNet) and face restoration (GFPGAN), on by default. The models are offered for download at launch, and a reminder suggests turning AI back on if it was switched off.
* **Updates**: through [GitHub releases](https://github.com/notsogeek87/lieluphoto/releases).

## Features inherited from PhotonCamera

* **LUTs**: import and apply `.cube`, `.png` (Halfs/Fulls) and `.xmp` files with a real-time preview.
* **Color recipes**: exposure, contrast, highlights, shadows, saturation, temperature, tint, vignette, grain, artistic effects and advanced filters.
* **Motion Photos**: multi-vendor support (Xiaomi, Samsung, Pixel, etc.).
* **High-speed burst**: no frame limit, with live LUT filtering.
* **Multi-frame synthesis**: stacking for better quality and less noise while keeping detail.
* **Phantom mode**: captures through the system camera and applies the LUT engine.

## Install

Download the APK from the latest [release](https://github.com/notsogeek87/lieluphoto/releases/latest) and install it. Later versions are offered inside the app.

### Signing

Releases are signed with the LieluPhoto release key (`CN=LieluPhoto, O=Lielu, C=FR`), SHA-256 fingerprint:

`5A:0B:BE:F8:AA:9F:28:16:90:30:64:7B:5E:C8:10:2B:47:A9:AC:05:E8:EF:AC:C4:48:58:3A:49:3D:9A:2D:C6`

Versions up to 1.28.4.292 were signed with `app/debug.keystore`, a public key: to move to a version signed with the release key, uninstall the old one once. `debug.keystore` is now only used for local builds and for test APKs of branches without secrets.

## Technology

* **UI**: Jetpack Compose
* **Camera API**: Camera2, no backward compatibility
* **Min SDK**: Android 11 (minSdk 30), native C++ code (CMake)
* **Documentation**: [docs/README.md](./docs/README.md) (French)

## License

Except for third-party components, models, fonts, LUTs and other assets that carry their own license notices in this repository, the source code is licensed under the [Apache License 2.0](./LICENSE), like PhotonCamera.
