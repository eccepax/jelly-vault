# AudioVault, BookVault & TuneVault

Three Android apps for a self-hosted [Jellyfin](https://jellyfin.org) server, each built around a single content type instead of Jellyfin's full media catalog:

- **AudioVault** — audiobook playback via a native ExoPlayer (Media3) audio experience.
- **BookVault** — EPUB/PDF reading, with scroll and paginated modes and dark-mode support.
- **TuneVault** — music playback for your Jellyfin music library.

All three are forks of [jellyfin-android](https://github.com/jellyfin/jellyfin-android). The original Jellyfin Android app is a full-featured media client built by the Jellyfin community — these apps strip it down, remove the general-purpose WebView/video pipeline, and rebuild around one content type each.

## Download

Signed APKs for each app are attached to [Releases](../../releases), one release per app. Install the APK for the app you want; the three apps install side by side.

AudioVault comes in two variants, signed with the same key so you can switch between them:

- `AudioVault-<version>-libre.apk` — no Google Play services. Pick this one if unsure.
- `AudioVault-<version>-chromecast.apk` — adds Chromecast support; requires Google Play services.

Each APK has a `.sha256` checksum next to it, and each release lists its signing certificate's fingerprint.

To get updates automatically, add this repository to [Obtainium](https://github.com/ImranR98/Obtainium).

## AudioVault: what's different from jellyfin-android

- Audio-only playback via ExoPlayer — no WebView, no video
- Audiobook library browser with folder navigation and a global flat view
- Resume position synced with Jellyfin (progress reported on play, pause, and stop)
- Sleep timer with end-of-chapter support
- Audiobook-optimized skip intervals (10 s back / 30 s forward)
- Chapter markers on the seek bar
- Per-book tracklist for multi-file audiobooks
- Playback speed control with hold-to-speed-up
- Offline downloads

## BookVault: what's different from jellyfin-android

- EPUB and PDF reading via a native reader — no general-purpose WebView browsing
- Scroll mode (continuous, chapter-nav buttons) and paginated mode (swipe between chapters)
- Dark mode with a properly inverted reading background, not just inverted text
- Adjustable EPUB text size and line spacing, tap-to-jump table of contents, and bookmarks
- Reading progress synced with Jellyfin

## TuneVault

- Home, albums, artists, and songs, with search
- Now Playing screen, mini-player, and an editable queue
- Favorites and playlists
- Offline downloads
- Sleep timer and volume levelling
- Playback reported to Jellyfin

## Building

Requires Android Studio or the Android command-line tools and a JDK.

```bash
# AudioVault
./gradlew :app-audiovault:assembleLibreDebug
./gradlew :app-audiovault:assembleProprietaryDebug

# BookVault
./gradlew :app-bookvault:assembleDebug

# TuneVault
./gradlew :app-tunevault:assembleDebug

# Unit tests and static analysis
./gradlew test
./gradlew detekt
```

The end-to-end tests (`scripts/run-e2e-tests.sh`) run against a real Jellyfin server. Point them at yours in `local.properties` (never commit this file):

```properties
e2e.hostname=http://10.0.2.2:8096
e2e.username=...
e2e.password=...
```

### Release builds

Each app is signed with its own key, kept outside the repo:

```bash
scripts/create-signing-keys.sh   # once: creates the keys in ../vault-keys and wires each app to its key
./gradlew :app-audiovault:assembleLibreRelease :app-audiovault:assembleProprietaryRelease           :app-bookvault:assembleRelease :app-tunevault:assembleRelease
scripts/package-release.sh       # collects signed APKs + checksums into build/release/
```

Bump `app.version` in the app's `gradle.properties` before each release.

## Requirements

- Android 8.0 (API 26) or later
- A running Jellyfin server with an audiobook, ebook, and/or music library

## Support

If you enjoy these apps, tips are appreciated: [cash.app/$bgkingsley](https://cash.app/$bgkingsley)

## Credits

These apps are built on top of [jellyfin-android](https://github.com/jellyfin/jellyfin-android), developed and maintained by the [Jellyfin contributors](https://github.com/jellyfin/jellyfin-android/graphs/contributors) and released under the GNU General Public License v2.0. The Jellyfin project and its community deserve full credit for the foundation these apps are built on. This repository's history starts at the public release; the upstream history lives in the jellyfin-android repository.

These apps are not affiliated with or endorsed by the Jellyfin project.

## AI usage

Significant portions of this codebase — including the original fork/restructure from jellyfin-android and most subsequent features, bug fixes, and the test suite — were written with the assistance of AI coding tools (Claude).

## License

GNU General Public License v2.0 — see [LICENSE](LICENSE.md) for details. This project inherits the license of the upstream jellyfin-android project.
