# Moonlight engine (vendored)

This module is the streaming engine from **Moonlight for Android**, used by PocketDeck to stream
games from a Sunshine (or other GameStream-compatible) host. Only the engine is included; no
Moonlight user interface, activity, layout, string or drawable is part of this module or of
PocketDeck.

- Upstream: https://github.com/moonlight-stream/moonlight-android
- Tag: `v12.2`, commit `b48494cb96bff23d8886c4775cc4f39a1075495d`
- Licence: GNU GPL v3 (see `LICENSE.txt`). PocketDeck as a whole is distributed under GPLv3.

## What was copied

From `app/src/main/java/com/limelight/`:

- `LimeLog.java`
- `nvstream/**` (GameStream protocol: HTTP/pairing, RTSP/stream setup via JNI, mDNS, Wake-on-LAN)
- `binding/PlatformBinding.java`, `binding/audio/**`, `binding/crypto/**`, `binding/video/**`
- `binding/input/ControllerHandler.java`, `KeyboardTranslator.java`, `touch/**`, `capture/**`,
  `evdev/**` (shim only), `driver/**`
- `preferences/PreferenceConfiguration.java`, `preferences/GlPreferences.java` (plain config objects)
- `ui/GameGestures.java` (callback interface only), `utils/Vector2d.java`

From `app/src/main/jni/`: `Android.mk`, `Application.mk`, `moonlight-core/**` including the
`moonlight-common-c` submodule (with its `enet` and `nanors` submodules) and the prebuilt Opus and
OpenSSL static libraries. Git metadata was removed.

## What was deliberately not copied

All UI and app shell: `Game`, `PcView`, `AppView`, `HelpActivity`, `ShortcutTrampoline`,
`PosterContentProvider`, `grid/**`, `ui/StreamView`, `ui/AdapterFragment*`, `utils/**` except
`Vector2d`, `preferences/**` except the two config classes, `computers/**`, `discovery/**`,
`binding/input/virtual_controller/**` (on-screen controller), `res/**`, and the root-only
`jni/evdev_reader`.

## Local modifications (GPLv3 section 5a)

| File | Change |
|---|---|
| `ui/GameGestures.java` | Added `showNotice(String)` and `onQuitCombo()` so the engine reports to the host app instead of drawing UI. |
| `binding/input/ControllerHandler.java` | Two `Toast`s replaced with `gestures.showNotice(...)`; quit combo calls `gestures.onQuitCombo()` instead of `activity.finish()`; removed `R` / `Toast` imports. |
| `binding/input/capture/InputCaptureManager.java` | `getInputCaptureProvider` takes the render `View` as a parameter instead of looking up `R.id.surfaceView`. |
| `binding/input/driver/UsbDriverService.java` | `Toast` replaced with a log line; removed `R` import. The service is not registered in any manifest. |
| `binding/video/MediaCodecDecoderRenderer.java` | Stats strings formatted inline (same English text as upstream `strings.xml`) instead of `R.string.*`. |
| `build.gradle.kts`, `consumer-rules.pro`, `src/main/AndroidManifest.xml` | New: library build for PocketDeck (non-root flavour, arm64-v8a / armeabi-v7a / x86_64). |
| `src/main/jni/CMakeLists.txt` | New: CMake port of upstream's `jni/Android.mk` + `moonlight-core/Android.mk` (same sources, defines, prebuilt Opus/OpenSSL, cpufeatures). ndk-build refuses paths with spaces. The top-level `Android.mk`/`Application.mk` were removed; the per-directory upstream `Android.mk` files are kept for reference but unused. |

Dependencies are the same as upstream's (BouncyCastle, jcodec, jmDNS, OkHttp, ShieldControllerExtensions
from JitPack restricted to the `com.github.cgutman` group in `settings.gradle.kts`).

Every modified line carries a `PocketDeck:` comment.

## Updating

Clone the new upstream tag with `--recurse-submodules`, copy the same file list over this module,
re-apply the modifications above, and update the tag/commit here.
