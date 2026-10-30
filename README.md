# AudioVault, BookVault, TuneVault & LiveVault

Four Android apps for a self-hosted [Jellyfin](https://jellyfin.org) server, one for each kind of media:

- **AudioVault**: audiobooks
- **BookVault**: ebooks (EPUB and PDF)
- **TuneVault**: music
- **LiveVault**: live TV, on phones, tablets, and Google TV

## Download

Signed APKs for each app are attached to [Releases](../../releases), one release per app. Install the APK for the app you want; the apps install side by side.

AudioVault and TuneVault cast to a Chromecast or a speaker with Google Cast built in, and LiveVault casts to a Chromecast or a TV with Google Cast built in. Casting needs Google Play services on the phone. Without Play services they work the same, just without the cast button; BookVault doesn't need Play services at all. LiveVault also runs on Google TV devices, Chromecast with Google TV included.

Each APK has a `.sha256` checksum next to it. To check an APK really comes from this project, compare its signing certificate (`apksigner verify --print-certs <apk>`, or what Obtainium/AppVerifier show) against:

| App | Signing certificate SHA-256 |
|---|---|
| AudioVault | `78:28:54:EB:BE:2A:16:8C:E3:D4:B4:82:82:6A:72:AA:B1:A7:E0:3C:EC:25:CA:BD:E4:21:55:7A:8E:56:69:B7` |
| BookVault | `40:26:AB:9A:92:47:E4:0C:53:02:2E:8A:8E:A4:FB:27:84:1A:51:CF:4F:29:71:52:7D:27:DA:06:99:0D:3B:17` |
| TuneVault | `DB:4A:AC:7E:A1:1E:A4:4A:E3:9F:AA:92:AE:32:6B:A7:DA:07:A8:ED:98:3A:6D:D4:3C:2E:C4:BE:CC:32:C5:1D` |
| LiveVault | `CB:6C:FC:7E:81:14:40:F2:08:A5:9A:E1:AD:5F:60:70:51:1E:F3:84:31:F0:7C:35:27:DF:E3:F1:F3:E4:06:76` |

To get updates automatically, add this repository to [Obtainium](https://github.com/ImranR98/Obtainium).

## AudioVault

- Audiobook library with folder browsing and an "all audiobooks" view
- Resume position synced with Jellyfin
- Sleep timer, including end of chapter
- 10 s back / 30 s forward skips, chapter markers, and a track list for multi-file books
- Playback speed control, with hold-to-speed-up
- Keeps playing in the background, with lock-screen and Bluetooth controls, and pauses when headphones are unplugged
- Offline downloads, optionally over Wi-Fi only
- Chromecast: the book plays on a cast device with its chapters, skips and speed, its place still synced, and picks up on the phone where it left off when you stop

## BookVault

- EPUB and PDF reading
- Scroll mode and paginated mode
- Dark mode with a properly inverted reading background
- Adjustable EPUB text size, font and line spacing
- Pinch or double-tap to zoom PDF pages
- Tap-to-jump table of contents, and bookmarks
- Reading progress synced with Jellyfin, so a book opens where you left it on your other devices too
- Offline downloads, optionally over Wi-Fi only

## TuneVault

- Home, albums, artists, and songs, with search
- Now Playing screen, mini-player, and an editable queue
- Favorites and playlists
- Offline downloads, optionally over Wi-Fi only
- Sleep timer and volume levelling
- Chromecast: plays the queue on a cast device, and picks up on the phone where it left off when you stop

## LiveVault

- Channel list with tags, favorites, search, and what's on now
- Channel up/down from a TV remote
- Picture-in-picture on phones and tablets
- Chromecast from phones and tablets: the channel plays on the TV while the phone keeps the remote's job (channel up/down, stop)
- Closes every stream when you stop, switch, or leave, so a tuner with a single connection is never left held
- If your TV provider's login is already in use, it tells you and offers free channels instead
- Continue watching your last channel

## Requirements

- Android 8.0 (API 26) or later
- Jellyfin server 10.10.7 or newer, with an audiobook, ebook, and/or music library, or Live TV set up for LiveVault

## Known issues

- **Casting shares your sign-in with devices on your network.** AudioVault, TuneVault and LiveVault cast through Google's standard receiver, which can only play a plain web address, so the stream address includes your Jellyfin access token. While something is casting, the cast device and any other device on the same network that can see the cast session can read that token, and it stays valid until it is revoked on the server. Signing out of the app doesn't revoke it. On a network you don't trust, don't cast, or cast while signed in as a user who isn't a Jellyfin administrator. If you think the token was exposed, a Jellyfin administrator can revoke it by removing the phone under Dashboard → Devices.
- **TuneVault's notification can show another app's cast.** If AudioVault starts casting to a device TuneVault is casting to, TuneVault's own screens show nothing playing, but its notification and lock-screen controls keep showing the book AudioVault is playing, and their buttons may control it. AudioVault's notification can do the same the other way round. Stopping the cast clears it.
- **LiveVault taking over a cast device hasn't been tested.** AudioVault and TuneVault each leave alone what the other sends to a cast device; LiveVault casts a different way, so starting LiveVault on a device one of the others is casting to (or the reverse) may not behave as well.

## Contributing

Bug reports, ideas and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

These apps are built on top of [jellyfin-android](https://github.com/jellyfin/jellyfin-android), developed and maintained by the [Jellyfin contributors](https://github.com/jellyfin/jellyfin-android/graphs/contributors) and released under the GNU General Public License v2.0. The Jellyfin project and its community deserve full credit for the foundation these apps are built on. This repository's history starts at the public release; the upstream history lives in the jellyfin-android repository.

These apps are not affiliated with or endorsed by the Jellyfin project.

## AI usage

Significant portions of this codebase — including the original fork/restructure from jellyfin-android and most subsequent features, bug fixes, and the test suite — were written with the assistance of AI coding tools (Claude).

## License

GNU General Public License v2.0 — see [LICENSE](LICENSE.md) for details. This project inherits the license of the upstream jellyfin-android project.

## Support

If you enjoy these apps, tips are appreciated: [cash.app/$bgkingsley](https://cash.app/$bgkingsley)
