# Test server

A disposable Jellyfin for the vault apps' contract and end-to-end tests: generated music, audiobooks and
Live TV, plus a few public-domain e-books. Nothing copyrighted and no media binaries are committed;
`up.sh` builds it all. Docker is the only requirement.

```bash
./up.sh      # generate media (first run only), start Jellyfin on :8097, seed and verify it
./reset.sh   # throw away the server's database (users, play state, playlists) and seed afresh
docker compose down      # stop it (keeps the database); add -v to delete it
```

Sign in as **vault-test / vault-test**. The credentials are committed on purpose: the server is local and
throwaway.

## What's in it

All of it comes from [`fixtures.json`](fixtures.json):

- **Music**: a 12-track FLAC album in a fixed order, used by most TuneVault tests, with one deliberately loud
  track for volume levelling and an `.m3u` in its folder. Then a few more albums (FLAC and mp3), a long noise
  track for the pause-and-resume download test, and filler albums for a library of over 100 tracks.
- **Audiobooks**: a book in several mp3 parts, a single-file m4b with chapters, a single-file mp3, and one
  with "History" in its title.
- **Books**: two generated PDFs (one first by title) and two Project Gutenberg EPUBs, downloaded once into
  `.data/downloads/`.
- **Live TV**: an M3U tuner and an XMLTV guide served by the `media-host` nginx container. Channels named
  `FREE|…` are tagged `Free`, and one non-free channel is tagged `Sports`. Streams are short looping test cards.
  The guide covers a day ago to two weeks ahead and is rewritten by every `up.sh`, so a server left running
  for over two weeks needs `up.sh` again.

`common-testing`'s `VaultFixtures.kt` names what the tests rely on, and `VaultFixturesContractTest` checks the
server holds all of it. **Change `fixtures.json`, `VaultFixtures.kt` and the tests together.** Media is rebuilt
whenever `fixtures.json` or `generate_media.py` changes.

## Jellyfin versions

The default version matches the maintainer's own server. Any other version can run instead, each with its
own database volume:

```bash
JELLYFIN_VERSION=latest ./reset.sh
```

The default is the version every PR is tested against. Renovate opens a PR when Jellyfin releases a
newer one, and that PR's test run shows whether the apps still work with it. The older versions the
apps support are tested weekly (see `.github/workflows/app-test.yaml`).

## Settings

| Variable | Default | |
|---|---|---|
| `JELLYFIN_VERSION` | `12.2` | Jellyfin image tag |
| `VAULT_TEST_PORT` | `8097` | Host port (8097 so it runs beside a real Jellyfin on 8096) |
| `SEED_TASK_TIMEOUT` | `600` | Seconds `seed.py` waits for each library scan, guide refresh or normalization |

## Files

- `generate_media.py`: builds `.data/media/` from `fixtures.json` with ffmpeg.
- `seed.py`: runs the startup wizard, adds the libraries (internet metadata off, nothing written next to the
  media), the tuner and the guide; runs the scan, the guide refresh and audio normalization; tags the
  channels; then checks every fixture is there. Standard library only, and safe to re-run.
- `tools/Dockerfile`: Python and ffmpeg, so both scripts run in a container. They also run directly with
  Python 3.10+ and ffmpeg on `PATH`: `python generate_media.py`, then `python seed.py --server http://localhost:8097`.
