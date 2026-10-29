# Contributing

Bug reports, ideas and pull requests are welcome.

- **Bugs and feature ideas:** open an [issue](../../issues/new/choose). Each app's Settings (or LiveVault's menu) has **Report a bug**, which fills in the app, version and device for you.
- **Questions:** ask in [Discussions](../../discussions).
- **Security problems:** see [SECURITY.md](SECURITY.md). Please don't open a public issue.

## Building

You need Android Studio (or the Android command-line tools) and a JDK.

```bash
./gradlew :app-audiovault:assembleDebug   # or app-bookvault, app-tunevault, app-livevault
./gradlew :app-audiovault:installDebug    # build and install on a connected device
```

Debug builds install next to the release apps, under their own IDs ending in `.debug`.

| Module | What's in it |
|---|---|
| `app-*` | One per app |
| `common` | Sign-in, server connection, settings and the shared design system |
| `common-media` | Playback pieces shared by the apps that play media |
| `common-downloads` | The download engine |
| `common-cast` | Chromecast, and the only module that needs Google Play services |
| `common-testing`, `test-support` | Test helpers |

## How the apps fit together

The apps are built the same way, so a fix or a pattern in one usually carries over to the others.

- **Compose only.** Each app has a single activity whose content is the common `VaultRoot`: connecting to a server and signing in, then the app's own screens. There are no fragments or XML layouts. Dependencies are wired with Koin, and each app keeps its own data in Room.
- **Navigation** is a saved back stack of screen names over the app's home screen, so it survives rotation and the app's process being stopped. `AudioVaultNavigation`, `BookVaultNavigation` and TuneVault's `LibraryNavigation` all use the common `VaultBackStack`. A screen that holds something open (a book being read) gets view models of its own, released when it closes.
- **Playback** (AudioVault, TuneVault) runs in a Media3 `MediaSessionService` (`AudiobookPlaybackService`, `PlaybackService`) that outlives the screens, so it keeps playing in the background and lock-screen and Bluetooth controls reach it. The screens talk to it through a `MediaController` (`DeferredConnection` in `common-media`). The sleep timer is shared, in `common`, and so is the wake mode (`WakeModeFollowsTrack` in `common-downloads`): a track playing from the device keeps only the CPU awake, a streamed one Wi-Fi too.
- **Chromecast** (AudioVault, TuneVault, LiveVault) is in `common-cast`, on Media3's cast players and Google's Default Media Receiver. `SessionCasting` puts the playback service's media session on Media3's `CastPlayer` (over the service's own player and a `RemoteCastPlayer`), which moves playback to the cast device while a cast session is up and back after (paused, see `PausedComingBack`). The cast device holds the queue itself, up to `CAST_QUEUE_LIMIT` songs (one Cast message): `CastQueueCap` keeps any controller to it, and TuneVault holds a longer queue a window at a time. Each song goes as `serverCastStreamNow` says: from the server, as it is where a cast device plays that format (within 48 kHz and 24 bits), else as an MP3 transcode, which can't seek. Media3's cast player never reports an error, so `CastFailureWatch` hears them from the Cast framework's statuses: a session's cast player gets it as its player error, and LiveVault shows the failure. LiveVault, with no media session, casts through `liveCasting()` (a `RemoteCastPlayer` alone). Reporting and progress keep working, since they follow the `MediaController`. Setting the player's volume (volume levelling, the sleep timer's fade) sets the cast device's stream volume while casting. Without Play services `casting()` gives `NoCasting`, and there's no cast button.
  - LiveVault has no media session, so it casts through `LiveCasting`, one live stream at a time. Its `CastingLivePlayer` plays a channel on the cast device while one is connected. The phone still opens and closes every stream: `LivePlayback` opens the channel for the cast device (`castVideoProfile`: HLS only, H.264 with AAC or MP3) and opens it again, closing the old stream first, when a cast starts or ends. While casting, going to the background doesn't free the stream; a cast that ends while the app is in the background does. If the app is killed while casting, the TV plays on until the server's transcode stops, and the stream ledger closes the stream on the next launch. There's no cast button on a TV.
- **Downloads** (all but LiveVault) go through the engine in `common-downloads`. Each app's Settings has the same downloads section, through `DownloadSettings`: Wi-Fi only (on until switched off), whether signing out removes them, the space they take, and Remove all. The downloads notification shows the app's own animal.
- **Places in a book** (AudioVault, BookVault) sync with the server through `ServerPositions` in `common`, which writes the user's data directly. When the phone and the server disagree, the newer place wins, and places saved while it can't be reached are kept and sent later. TuneVault reports plays instead, queued while offline. All three keep what the server couldn't take just then (no network, or a 401/403/408/429/5xx: `worthRetrying`) and send it later.
- **Signing out** goes through `SigningOut` in `common` in every app: one at a time, and one that fails says so and can be tried again.
- **BookVault's reader** draws PDFs as a `LazyColumn` (scroll) or a `HorizontalPager` (a page at a time), and EPUB chapters as locked-down WebViews (no scripts, only the book's own files) inside a `HorizontalPager`. PDFs zoom with `Modifier.pageZoom`.

## Before opening a pull request

GitHub runs these on every pull request, and they need to pass:

```bash
./gradlew test                                            # unit tests
./gradlew detekt lint                                     # static analysis (detekt fixes some style issues itself)
./gradlew verifyRoborazziDebug --tests '*ScreenshotTest'  # screenshot tests
```

The screenshot tests compare each app's main screens with approved images in `src/test/screenshots/`. If you changed how a screen looks on purpose, approve the new look with `./gradlew recordRoborazziDebug` and commit the images.

Tests for new behaviour are appreciated, and expected for bug fixes.

### Server tests

The "contract" tests (`*ContractTest`, part of `./gradlew test`) check the apps' assumptions about a real Jellyfin, and the end-to-end tests (`scripts/run-e2e-tests.sh`) run the apps on an emulator against one. Both use a disposable server in [`test-server/`](test-server/README.md), seeded with generated media. It needs only Docker:

```bash
test-server/up.sh    # Jellyfin on :8097 with music, audiobooks, books and Live TV
export VAULT_TEST_USERNAME=vault-test VAULT_TEST_PASSWORD=vault-test
./gradlew test                                          # contract tests now run instead of skipping
E2E_MODULE=app-tunevault scripts/run-e2e-tests.sh       # end-to-end, on an emulator
E2E_MODULE=app-bookvault E2E_SUITE=smoke scripts/run-e2e-tests.sh   # just the critical paths
```

`E2E_SUITE` runs part of an app's end-to-end tests (AudioVault, BookVault and TuneVault): `smoke`, or one area such as `reading` (BookVault) or `playback`. Each app's areas are listed in its `E2ESuites.kt`, and every test class belongs to exactly one.

The end-to-end tests clear the app's data, so use an emulator. Without credentials the server tests are skipped, so a plain `./gradlew test` passes with no server. With them but no server reachable, they fail.

`VAULT_TEST_HOSTNAME` (default `http://localhost:8097`) and `VAULT_TEST_E2E_HOSTNAME` (default `http://10.0.2.2:8097`, the server as the emulator sees it) point the tests at another server. Instead of the environment, all four settings can live in `local.properties`, which is never committed:

```properties
contract.hostname=http://localhost:8097
e2e.hostname=http://10.0.2.2:8097
e2e.username=vault-test
e2e.password=vault-test
```

When any `VAULT_TEST_*` variable is set, `local.properties` is ignored, so one server's credentials are never sent to another's address.

## License

By contributing, you agree that your contributions are licensed under the [GNU General Public License v2.0](LICENSE.md), like the rest of the project.
