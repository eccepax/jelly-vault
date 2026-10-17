"""Configures a fresh test Jellyfin from fixtures.json, then checks the server holds every fixture.

Idempotent: each step looks before it acts, so running it again on a seeded server only refreshes
the guide and re-checks. Standard library only, and only API calls Jellyfin 10.9+ shares.

Usage: python seed.py [--server URL]   (default: $SEED_SERVER, else http://jellyfin:8096)
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIXTURES = json.loads((HERE / "fixtures.json").read_text(encoding="utf-8"))
USER, PASSWORD = FIXTURES["user"]["name"], FIXTURES["user"]["password"]
TASK_TIMEOUT_S = int(os.environ.get("SEED_TASK_TIMEOUT", "600"))
AUTH = 'MediaBrowser Client="vault-test-seed", Device="seed", DeviceId="vault-test-seed", Version="1.0"'

# Where the compose file mounts each library, and its Jellyfin collection type.
LIBRARIES = [
    ("Music", "music", "/media/music"),
    ("Audiobooks", "books", "/media/audiobooks"),
    ("Books", "books", "/media/books"),
]
# No internet metadata or images, and nothing written beside the media: fixtures stay exactly as
# generated, and a scan never depends on MusicBrainz being up.
NO_FETCHERS = ["MusicArtist", "MusicAlbum", "Audio", "AudioBook", "Book"]
INTERNET_METADATA = ["MusicBrainz", "TheAudioDB", "Google Books", "Open Library"]
INTERNET_IMAGES = ["TheAudioDB", "Google Books", "Open Library"]
LIVE_TV_HOST = "http://media-host"

server = ""
token = None


def log(msg):
    print(f"[seed {datetime.now():%H:%M:%S}] {msg}", flush=True)


class ApiError(Exception):
    def __init__(self, code, text):
        super().__init__(f"HTTP {code}: {text}")
        self.code = code


def call(path, data=None, method=None):
    headers = {"Content-Type": "application/json", "Authorization": AUTH + (f', Token="{token}"' if token else "")}
    body = json.dumps(data).encode() if data is not None else None
    method = method or ("POST" if body is not None else "GET")
    request = urllib.request.Request(server + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
            return json.loads(raw) if raw.strip() else None
    except urllib.error.HTTPError as e:
        raise ApiError(e.code, f"{method} {path}: {e.read()[:300].decode(errors='replace')}") from None


def wait_for_server(timeout_s=180):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            info = call("/System/Info/Public")
            if info and info.get("Version"):
                return info
        except (urllib.error.URLError, ApiError, ConnectionError, OSError):
            pass
        time.sleep(2)
    sys.exit(f"seed: no Jellyfin answering at {server} after {timeout_s}s")


def wizard(info):
    if info.get("StartupWizardCompleted"):
        log("startup wizard: already done")
        return
    call("/Startup/Configuration", {"UICulture": "en-US", "MetadataCountryCode": "US", "PreferredMetadataLanguage": "en"})
    call("/Startup/User")  # makes the server create its first user
    call("/Startup/User", {"Name": USER, "Password": PASSWORD})
    call("/Startup/RemoteAccess", {"EnableRemoteAccess": True})
    call("/Startup/Complete", method="POST")
    log(f"startup wizard: done, admin {USER!r}")


def sign_in():
    global token
    auth = call("/Users/AuthenticateByName", {"Username": USER, "Pw": PASSWORD})
    token = auth["AccessToken"]
    return auth["User"]["Id"]


def server_settings():
    config = call("/System/Configuration")
    config["QuickConnectAvailable"] = True
    # Artists sit outside any library folder, so library options don't reach them: switch internet
    # metadata off server-wide too, per item type.
    options = {o["ItemType"]: o for o in config.get("MetadataOptions", [])}
    for item_type in NO_FETCHERS:
        option = options.setdefault(item_type, {"ItemType": item_type})
        option["DisabledMetadataFetchers"] = INTERNET_METADATA
        option["DisabledImageFetchers"] = INTERNET_IMAGES
        option["DisabledMetadataSavers"] = ["Nfo"]
    config["MetadataOptions"] = list(options.values())
    call("/System/Configuration", config)
    log("server: quick connect on, internet metadata off")


def library_options(collection_type, path):
    return {
        "EnableRealtimeMonitor": False,
        "SaveLocalMetadata": False,
        "MetadataSavers": [],
        "EnableLUFSScan": collection_type == "music",
        "TypeOptions": [{"Type": t, "MetadataFetchers": [], "ImageFetchers": []} for t in NO_FETCHERS],
        "PathInfos": [{"Path": path}],
    }


def libraries():
    existing = {f["Name"] for f in call("/Library/VirtualFolders")}
    for name, collection_type, path in LIBRARIES:
        if name in existing:
            continue
        query = urllib.parse.urlencode({"name": name, "collectionType": collection_type, "paths": path,
                                        "refreshLibrary": "false"})
        call(f"/Library/VirtualFolders?{query}", {"LibraryOptions": library_options(collection_type, path)})
        log(f"library: added {name} ({collection_type}) at {path}")


def live_tv():
    config = call("/System/Configuration/livetv")
    if not config.get("TunerHosts"):
        call("/LiveTv/TunerHosts", {
            "Type": "m3u", "Url": f"{LIVE_TV_HOST}/channels.m3u", "FriendlyName": "Test channels",
            "ImportFavoritesOnly": False, "AllowHWTranscoding": False, "EnableStreamLooping": True, "TunerCount": 0,
        })
        log("live tv: added the M3U tuner")
    if not config.get("ListingProviders"):
        call("/LiveTv/ListingProviders?validateListings=false&validateLogin=false",
             {"Type": "xmltv", "Path": f"{LIVE_TV_HOST}/guide.xml", "EnableAllTuners": True})
        log("live tv: added the XMLTV guide")


def wait_for_task(task, key, done):
    """Polls [task] until done(state); returns that state."""
    deadline, last = time.time() + TASK_TIMEOUT_S, None
    while time.time() < deadline:
        state = call(f"/ScheduledTasks/{task['Id']}")
        if done(state):
            return state
        progress = f"{state['State']} {state.get('CurrentProgressPercentage') or 0:.0f}%"
        if progress != last:
            log(f"task {key}: {progress}")
            last = progress
        time.sleep(2)
    sys.exit(f"seed: task {key} still running after {TASK_TIMEOUT_S}s (raise SEED_TASK_TIMEOUT?)")


def last_run(state):
    return state.get("LastExecutionResult") or {}


def run_task(key):
    """Runs a scheduled task and waits for that run to end. The task can still read Idle just after
    the trigger, so a run counts as ended only once its result is newer than the one from before."""
    task = next((t for t in call("/ScheduledTasks") if t["Key"] == key), None)
    if task is None:
        sys.exit(f"seed: this server has no {key!r} task")
    # A run already going (a startup scan, say) may have begun before the settings above; a trigger
    # now would be ignored, so let it finish and run the task again.
    before = last_run(wait_for_task(task, key, lambda s: s["State"] == "Idle")).get("EndTimeUtc")
    call(f"/ScheduledTasks/Running/{task['Id']}", method="POST")
    state = wait_for_task(task, key, lambda s: s["State"] == "Idle" and last_run(s).get("EndTimeUtc") != before)
    result = last_run(state)
    log(f"task {key}: {result.get('Status')}")
    if result.get("Status") != "Completed":
        sys.exit(f"seed: task {key} ended {result.get('Status')}: {result.get('ErrorMessage')}")


def channels(user_id):
    return call(f"/LiveTv/Channels?userId={user_id}&addCurrentProgram=true&fields=Tags&enableImageTypes=Primary")["Items"]


def tag_channels(user_id):
    """After the guide refresh, which is what creates the channels. Tags survive later refreshes."""
    wanted = {c["name"]: sorted(c["tags"]) for c in FIXTURES["livetv"]["channels"]}
    for channel in channels(user_id):
        tags = wanted.get(channel["Name"])
        if tags is None or sorted(channel.get("Tags") or []) == tags:
            continue
        item = call(f"/Users/{user_id}/Items/{channel['Id']}")
        item["Tags"] = tags
        call(f"/Items/{channel['Id']}", item)
    log("live tv: channel tags set")


def items(user_id, **query):
    query = {"userId": user_id, "recursive": "true", **query}
    return call(f"/Items?{urllib.parse.urlencode(query)}")["Items"]


def verify(user_id):
    """Every fixture a test relies on, checked once, so a bad seed fails here with a clear list."""
    problems = []

    def expect(ok, what):
        if not ok:
            problems.append(what)

    albums = {a["Name"]: a for a in items(user_id, includeItemTypes="MusicAlbum")}
    for album in FIXTURES["music"]["albums"]:
        found = albums.get(album["title"])
        expect(found, f"album {album['title']!r}")
        if not found:
            continue
        expect(album["artist"] in (found.get("AlbumArtist") or ""), f"album {album['title']!r} by {album['artist']!r}")
        expect("Primary" in (found.get("ImageTags") or {}), f"album {album['title']!r} has a cover")
        tracks = items(user_id, parentId=found["Id"], includeItemTypes="Audio", sortBy="ParentIndexNumber,IndexNumber",
                       fields="NormalizationGain")
        expect([t["Name"] for t in tracks] == [t["title"] for t in album["tracks"]],
               f"album {album['title']!r} tracks in order (got {[t['Name'] for t in tracks]})")
        for fixture, track in zip(album["tracks"], tracks):
            if fixture.get("loud"):
                gain = track.get("NormalizationGain")
                expect(gain is not None and gain < -5, f"loud track {fixture['title']!r} normalization gain (got {gain})")
    filler = FIXTURES["music"]["filler"]
    track_count = sum(len(a["tracks"]) for a in FIXTURES["music"]["albums"]) + filler["albums"] * filler["tracks_per_album"]
    music_tracks = len(items(user_id, includeItemTypes="Audio"))
    expect(music_tracks == track_count, f"{track_count} music tracks (got {music_tracks})")
    playlists = items(user_id, includeItemTypes="Playlist", fields="Path")
    expect(any((p.get("Path") or "").lower().endswith(".m3u") for p in playlists), "an .m3u playlist from an album folder")

    audiobooks = items(user_id, includeItemTypes="AudioBook")
    for book in FIXTURES["audiobooks"]:
        mine = [b for b in audiobooks if b.get("Album") == book["title"] or b["Name"] == book["title"]]
        expect(len(mine) == book.get("parts", 1), f"audiobook {book['title']!r} in {book.get('parts', 1)} file(s) (got {len(mine)})")

    book_names = {b["Name"] for b in items(user_id, includeItemTypes="Book")}
    for book in FIXTURES["books"]["generated_pdfs"] + FIXTURES["books"]["public_domain_epubs"]:
        expect(book["title"] in book_names, f"book {book['title']!r} (have {sorted(book_names)})")

    now = datetime.now(timezone.utc)
    on_server = {c["Name"]: c for c in channels(user_id)}
    for fixture in FIXTURES["livetv"]["channels"]:
        channel = on_server.get(fixture["name"])
        expect(channel, f"channel {fixture['name']!r}")
        if not channel:
            continue
        expect(sorted(channel.get("Tags") or []) == sorted(fixture["tags"]), f"channel {fixture['name']!r} tags {fixture['tags']}")
        expect("Primary" in (channel.get("ImageTags") or {}), f"channel {fixture['name']!r} logo")
        program = channel.get("CurrentProgram") or {}
        end = program.get("EndDate")
        expect(end and datetime.fromisoformat(end.replace("Z", "+00:00")) > now, f"channel {fixture['name']!r} has a current programme")

    expect(call("/QuickConnect/Enabled") is True, "quick connect enabled")
    if problems:
        sys.exit("seed: the server is missing fixtures:\n  - " + "\n  - ".join(problems))
    log(f"verified: {len(FIXTURES['music']['albums'])} named albums, {music_tracks} tracks, "
        f"{len(FIXTURES['audiobooks'])} audiobooks, {len(book_names)} books, {len(on_server)} channels")


def main():
    global server
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--server", default=os.environ.get("SEED_SERVER", "http://jellyfin:8096"))
    server = parser.parse_args().server.rstrip("/")

    info = wait_for_server()
    log(f"Jellyfin {info['Version']} at {server}")
    wizard(info)
    user_id = sign_in()
    server_settings()
    libraries()
    live_tv()
    run_task("RefreshLibrary")
    run_task("RefreshGuide")
    run_task("AudioNormalization")
    tag_channels(user_id)
    verify(user_id)
    log(f"ready: Jellyfin {info['Version']}, sign in as {USER} / {PASSWORD}")


if __name__ == "__main__":
    main()
