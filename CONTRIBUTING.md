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
| `common-testing`, `test-support` | Test helpers |

## Before opening a pull request

GitHub runs these on every pull request, and they need to pass:

```bash
./gradlew test                                            # unit tests
./gradlew detekt lint                                     # static analysis (detekt fixes some style issues itself)
./gradlew verifyRoborazziDebug --tests '*ScreenshotTest'  # screenshot tests
```

The screenshot tests compare each app's main screens with approved images in `src/test/screenshots/`. If you changed how a screen looks on purpose, approve the new look with `./gradlew recordRoborazziDebug` and commit the images.

Tests for new behaviour are appreciated, and expected for bug fixes.

### End-to-end tests

`scripts/run-e2e-tests.sh` runs the instrumented tests on a device or emulator against a real Jellyfin server. They clear the app's data, so use an emulator. Point them at a test server in `local.properties`, which is never committed:

```properties
# The server as the emulator sees it
e2e.hostname=http://10.0.2.2:8096
# The server as your computer sees it
contract.hostname=http://localhost:8096
e2e.username=...
e2e.password=...
```

With a username and password set, `./gradlew test` also runs the "contract" tests, which check the apps' assumptions about the server against `contract.hostname`. Without them, those tests are skipped. With them but no server reachable, they fail.

## License

By contributing, you agree that your contributions are licensed under the [GNU General Public License v2.0](LICENSE.md), like the rest of the project.
