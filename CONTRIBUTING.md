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

### Server tests

The "contract" tests (`*ContractTest`, part of `./gradlew test`) check the apps' assumptions about a real Jellyfin, and the end-to-end tests (`scripts/run-e2e-tests.sh`) run the apps on an emulator against one. Both use a disposable server in [`test-server/`](test-server/README.md), seeded with generated media. It needs only Docker:

```bash
test-server/up.sh    # Jellyfin on :8097 with music, audiobooks, books and Live TV
export VAULT_TEST_USERNAME=vault-test VAULT_TEST_PASSWORD=vault-test
./gradlew test                                          # contract tests now run instead of skipping
E2E_MODULE=app-tunevault scripts/run-e2e-tests.sh       # end-to-end, on an emulator
```

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
