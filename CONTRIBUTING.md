# Contributing to PocketDeck

Thanks for helping out! Bug reports, ideas and pull requests are all welcome.

## Reporting bugs and asking for features

- Search [existing issues](https://github.com/MohammedAbidNafi/Pocket-Deck/issues) first.
- Use the **Bug report** or **Feature request** template, and include your device, Android version and PocketDeck version (Settings → About).
- PC streaming problems: add your Sunshine version and check the [troubleshooting list](README.md#pc-streaming) (firewall, ViGEmBus, same network) first.
- Security issues: don't open a public issue. See [SECURITY.md](SECURITY.md).

## Development setup

1. Install a recent Android Studio (JDK 17+, Android SDK with compile SDK 37).
2. Clone the repo and open it. The first build downloads NDK `29.0.14206865` and CMake `3.22.1` for the streaming engine.
3. Build and test:

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

## Making changes

- Branch from `main` and open a pull request; direct pushes to `main` are blocked.
- Keep PRs focused on one change. CI must pass (debug build + unit tests) before merging.
- PRs are squash-merged, so the PR title becomes the commit message: write it as a short summary (e.g. `fix: home list overscroll on touch`).
- Add or update unit tests for logic in `data/` (it's plain Kotlin and easy to test).
- Match the existing style: Kotlin, Jetpack Compose, controller-first UI (everything must work with a D-pad and face buttons, not just touch).

### Releasing

Releases are automatic. To ship a new version, bump `versionName` and `versionCode` in [`app/build.gradle.kts`](app/build.gradle.kts) in a PR. When it merges into `main`, the **Release** workflow sees there's no `v<versionName>` tag yet, builds the release APK, and publishes a GitHub Release with that tag, the APK and generated notes. Pushes that don't change the version do nothing.

Release APKs are signed with the maintainer's key when these repository secrets are set: `RELEASE_KEYSTORE_BASE64` (the keystore file, base64-encoded), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Without them, the APK is signed with a temporary debug key and the release notes say so. Locally, set `POCKETDECK_KEYSTORE_FILE`, `POCKETDECK_KEYSTORE_PASSWORD`, `POCKETDECK_KEY_ALIAS` and `POCKETDECK_KEY_PASSWORD` to sign a release build the same way.

### The Moonlight engine (`moonlight/`)

`moonlight/` is a vendored copy of Moonlight's streaming engine. PocketDeck uses **no Moonlight UI**: if an engine class shows UI (a toast, dialog, `R.*` resource or `finish()`), route it through the `GameGestures` callback and draw it in PocketDeck instead. Record every change to vendored files in [`moonlight/UPSTREAM.md`](moonlight/UPSTREAM.md) and mark the changed lines with a `PocketDeck:` comment.

## Licence

PocketDeck is licensed under the [GNU GPL v3](LICENSE). By contributing, you agree that your contributions are licensed under the same terms. New third-party code must be GPLv3-compatible and listed in [`NOTICE`](NOTICE) and on the About screen.

## Code of conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
