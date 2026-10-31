# Privacy Policy

_Last updated: October 31, 2026_

This policy covers AudioVault, BookVault, TuneVault and LiveVault (the "apps"), published by eccepax. The apps are clients for a Jellyfin media server that you choose and connect to yourself.

## What we collect

Nothing. The developer runs no servers for these apps and does not collect, store, sell or share any personal data. The apps contain no analytics, advertising or crash-reporting code.

## Data the apps handle on your behalf

To work, the apps send data only to the Jellyfin server you enter:

- **Sign-in:** your server address and username are sent to your server so you can sign in. Your password is sent to your server only and is not stored on the device. The apps keep the access token your server issues.
- **Playback and reading progress:** listening positions, reading positions and plays are sent to your server so your progress syncs between devices.
- **Device name:** a device name and ID are sent to your server, the same as every Jellyfin client, so the server can list your signed-in devices.

That data is stored on your Jellyfin server, which is controlled by whoever runs it. Their privacy practices apply to it, not this policy.

## Data stored on your device

The apps store your server address, sign-in token, settings, progress, bookmarks and any downloaded audiobooks, books or music on your device. Removing an app deletes this data. You can also remove downloads, or sign out, in each app's Settings.

## Casting

AudioVault, TuneVault and LiveVault support Google Cast. When you cast, the app sends the media address on your server to the Cast device you pick, and Google's Cast SDK runs on your device. Google's handling of that data is covered by Google's privacy policy: https://policies.google.com/privacy

## Connection security

If you connect to a server address that starts with `http://` instead of `https://`, traffic to it is not encrypted, and the apps warn you about this. Use `https://` when your server supports it.

## Children

The apps are not directed at children under 13.

## Changes

If this policy changes, the updated version will be posted at this address with a new date.

## Contact

Questions: open an issue at https://github.com/eccepax/jelly-vault/issues
