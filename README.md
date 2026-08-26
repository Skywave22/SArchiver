# Sarchiver

Production Android file manager, archive utility, USB OTG browser, and MTP client.

Light-green Material 3 UI. Kotlin, Jetpack Compose, coroutines.

## Features that actually run

- Browse internal storage, app files, and `StorageManager` volumes (removable / OTG when Android exposes a path or mounted volume).
- Create / rename / delete files and folders.
- Copy / cut / paste via a streaming transfer engine (64 KiB–1 MiB buffers, never loads whole files).
- ZIP / 7z / TAR / tar.gz / tar.xz create and extract (Apache Commons Compress + XZ).
- RAR **extraction only** (junrar). RAR **creation is not offered** (RAR licensing).
- Password ZIP/7z extract when the library supports it.
- Archive listing, extract here, integrity smoke test.
- Path-traversal protection on extract (`PathSecurity`).
- MTP: real USB class 6 bulk protocol (OpenSession, GetStorageIDs, GetObjectHandles, GetObjectInfo, GetObject, DeleteObject). Devices that do not speak MTP correctly fail with an error — nothing is simulated.
- USB mass-storage / OTG: listed through `StorageManager` volumes when the system mounts them. Unmounted or permission-denied devices are reported, not faked.
- Foreground `TransferService` so copies survive leaving the UI.
- Light / dark / system theme.
- Search in the current folder, sort, list/grid, bookmarks, properties (async folder size).

## Requirements

- Android 8.0 (API 26) through Android 15 (API 35).
- All-files access (`MANAGE_EXTERNAL_STORAGE`) on Android 11+ for full internal browsing. The app opens the system settings screen on first launch.
- USB host for OTG/MTP.

## Platform limitations (honest)

- Scoped storage still applies; some OEM paths are not listable without user-granted SAF trees.
- MTP is vendor-specific. Only operations the device acknowledges succeed.
- USB OTG filesystems Android cannot mount cannot be browsed.
- Split/multipart RAR and RAR5 features depend on junrar support.
- 7z write currently buffers each file when adding (extract streams). Prefer ZIP for very large create jobs.

## Build

JDK 17, Android SDK 35.

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

Unsigned release APK: `app/build/outputs/apk/release/`.

To sign in CI, add GitHub Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and wire `signingConfigs` — **do not commit a keystore**.

## GitHub Actions

`.github/workflows/android.yml` runs unit tests, builds the release APK, uploads it as an artifact, and on `v*` tags publishes a GitHub Release using the built-in `GITHUB_TOKEN`.

## Architecture

- `data/storage` — `File` + `StorageManager` + SAF `DocumentFile`
- `data/archive` — Commons Compress + junrar
- `data/transfer` — streaming copy + foreground service
- `data/mtp` — USB host MTP initiator
- `ui` — Compose Material 3 + `BrowserViewModel`

## License

See `LICENSE`.
