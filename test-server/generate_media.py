"""Builds the test server's media from fixtures.json into .data/media.

Generated with ffmpeg (tones, noise, test-card video) plus a few public-domain EPUBs, so nothing
copyrighted is involved and no binaries are committed. Media is rebuilt only when fixtures.json or
this script changes; the Live TV guide is rewritten on every run so "what's on now" never runs out.

Usage: python generate_media.py [--out DIR]   (needs ffmpeg on PATH)
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
MEDIA_HOST = "http://media-host"  # how Jellyfin reaches the nginx service on the compose network
GUIDE_SLOT = timedelta(minutes=30)
GUIDE_PAST, GUIDE_FUTURE = timedelta(days=1), timedelta(days=14)
DOWNLOAD_RETRY_WAITS_S = [5, 20, 60]


def log(msg):
    print(f"[generate] {msg}", flush=True)


def safe(name):
    """A file or folder name every OS accepts: Windows forbids <>:"/\\|?* in names."""
    return re.sub(r'[<>:"/\\|?*]', "", name).strip(" .")


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def render(path, *args):
    """Runs ffmpeg into a temporary name beside [path], so an interrupted run leaves no half file."""
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".part-{path.name}")
    ffmpeg(*args, str(tmp))
    tmp.replace(path)


def tags(**values):
    out = []
    for key, value in values.items():
        if value is not None:
            out += ["-metadata", f"{key}={value}"]
    return out


def frequency(seed):
    """A stable, distinct tone per track, from 220 to 880 Hz."""
    return 220 + int(hashlib.sha256(seed.encode()).hexdigest(), 16) % 660


def audio_source(seed, seconds, loud=False, noise=False):
    if noise:
        source = f"anoisesrc=color=white:amplitude=0.5:sample_rate=44100:duration={seconds}"
        return ["-f", "lavfi", "-i", source, "-ac", "2"]
    source = f"sine=frequency={frequency(seed)}:sample_rate=22050:duration={seconds}"
    # A sine source sits at about -18 dBFS; +16 dB makes the loud track the one levelling turns down.
    gain = ["-af", "volume=16dB"] if loud else []
    return ["-f", "lavfi", "-i", source, *gain, "-ac", "1"]


CODECS = {
    "flac": ["-c:a", "flac"],
    "mp3": ["-c:a", "libmp3lame", "-b:a", "64k"],
}


def cover(path, seed):
    colour = hashlib.sha256(seed.encode()).hexdigest()[:6]
    render(path, "-f", "lavfi", "-i", f"color=c=0x{colour}:size=300x300", "-frames:v", "1")


def music(fixtures, root):
    albums = list(fixtures["albums"])
    filler = fixtures["filler"]
    for n in range(1, filler["albums"] + 1):
        albums.append({
            "artist": filler["artist"], "title": f"Filler Album {n:02d}", "year": 2000 + n,
            "format": filler["format"],
            "tracks": [{"title": f"Filler {n:02d}-{t:02d}", "seconds": filler["seconds"]}
                       for t in range(1, filler["tracks_per_album"] + 1)],
        })
    for album in albums:
        folder = root / safe(album["artist"]) / safe(album["title"])
        ext, total = album["format"], len(album["tracks"])
        names = []
        for number, track in enumerate(album["tracks"], start=1):
            name = f"{number:02d} {safe(track['title'])}.{ext}"
            names.append(name)
            render(
                folder / name,
                *audio_source(album["title"] + track["title"], track["seconds"],
                              track.get("loud", False), track.get("noise", False)),
                *CODECS[ext],
                *tags(title=track["title"], artist=album["artist"], album_artist=album["artist"],
                      album=album["title"], track=f"{number}/{total}", date=album["year"], genre="Test"),
            )
        cover(folder / "cover.jpg", album["title"])
        if album.get("m3u"):
            (folder / f"{safe(album['title'])}.m3u").write_text("\n".join(names) + "\n", encoding="utf-8")
    log(f"music: {len(albums)} albums, {sum(len(a['tracks']) for a in albums)} tracks")


def audiobooks(fixtures, root):
    for book in fixtures:
        folder = root / safe(book["author"]) / safe(book["title"])
        common = dict(artist=book["author"], album_artist=book["author"], album=book["title"], genre="Audiobook")
        seed = book["title"]
        if book["format"] == "mp3-parts":
            parts = book["parts"]
            for n in range(1, parts + 1):
                render(folder / f"{n:02d} Part {n}.mp3",
                       *audio_source(f"{seed}{n}", book["seconds"]), *CODECS["mp3"],
                       *tags(title=f"Part {n}", track=f"{n}/{parts}", **common))
        elif book["format"] == "m4b":
            meta = folder / ".chapters.txt"
            folder.mkdir(parents=True, exist_ok=True)
            step = book["seconds"] * 1000 // len(book["chapters"])
            lines = [";FFMETADATA1"]
            for i, title in enumerate(book["chapters"]):
                lines += ["[CHAPTER]", "TIMEBASE=1/1000", f"START={i * step}", f"END={(i + 1) * step}", f"title={title}"]
            meta.write_text("\n".join(lines) + "\n", encoding="utf-8")
            render(folder / f"{safe(book['title'])}.m4b",
                   *audio_source(seed, book["seconds"]), "-i", str(meta), "-map", "0:a", "-map_metadata", "1",
                   "-map_chapters", "1", "-c:a", "aac", "-b:a", "48k",
                   *tags(title=book["title"], **common))
            meta.unlink()
        elif book["format"] == "mp3":
            render(folder / f"{safe(book['title'])}.mp3",
                   *audio_source(seed, book["seconds"]), *CODECS["mp3"],
                   *tags(title=book["title"], **common))
        else:
            sys.exit(f"unknown audiobook format {book['format']!r}")
        cover(folder / "cover.jpg", seed)
    log(f"audiobooks: {len(fixtures)}")


def pdf(path, title, pages):
    """A minimal valid PDF: one line of Helvetica per page, and its title in the document info."""
    if path.exists():
        return
    objects = ["<< /Type /Catalog /Pages 2 0 R >>", None, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    kids = []
    for n in range(1, pages + 1):
        text = f"{title} - page {n}".replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        stream = f"BT /F1 18 Tf 72 720 Td ({text}) Tj ET"
        objects.append(f"<< /Length {len(stream)} >>\nstream\n{stream}\nendstream")
        objects.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                       f"/Resources << /Font << /F1 3 0 R >> >> /Contents {len(objects)} 0 R >>")
        kids.append(f"{len(objects)} 0 R")
    objects[1] = f"<< /Type /Pages /Kids [{' '.join(kids)}] /Count {pages} >>"
    safe_title = title.replace("(", "\\(").replace(")", "\\)")
    objects.append(f"<< /Title ({safe_title}) >>")
    out, offsets = bytearray(b"%PDF-1.4\n"), []
    for i, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    out += "".join(f"{o:010d} 00000 n \n" for o in offsets).encode()
    out += (f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R /Info {len(objects)} 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(out))


def download(url):
    """[url]'s body, retried with backoff: Gutenberg is a volunteer site and sometimes slow or busy."""
    request = urllib.request.Request(url, headers={"User-Agent": "vault-test-server (test fixtures)"})
    for attempt, wait in enumerate(DOWNLOAD_RETRY_WAITS_S + [None], start=1):
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except (urllib.error.URLError, OSError) as e:
            if wait is None:
                sys.exit(f"could not download {url} after {attempt} tries: {e}")
            log(f"download failed ({e}); retrying in {wait}s")
            time.sleep(wait)


def epub(url, path, downloads):
    """Fetches a public-domain EPUB once into the download cache, and checks it really is an EPUB."""
    cached = downloads / path.name
    if not cached.exists():
        log(f"downloading {url}")
        downloads.mkdir(parents=True, exist_ok=True)
        data = download(url)
        tmp = cached.with_name(f".part-{cached.name}")
        tmp.write_bytes(data)
        with zipfile.ZipFile(tmp) as z:
            if z.read("mimetype").strip() != b"application/epub+zip":
                sys.exit(f"{url} is not an EPUB")
        tmp.replace(cached)
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(cached, path)


def books(fixtures, root, downloads):
    for book in fixtures["generated_pdfs"]:
        pdf(root / f"{safe(book['title'])}.pdf", book["title"], book["pages"])
    for book in fixtures["public_domain_epubs"]:
        epub(book["url"], root / book["file"], downloads)
    # Distinct, ordered modification times, so "date added" has one right order.
    files = sorted(p for p in root.iterdir() if p.is_file())
    base = time.time() - len(files) * 86400
    for i, path in enumerate(files):
        os.utime(path, (base + i * 86400, base + i * 86400))
    log(f"books: {len(files)}")


def livetv(fixtures, root):
    seconds = fixtures["stream_seconds"]
    lines = ["#EXTM3U"]
    for channel in fixtures["channels"]:
        slug = safe(channel["id"])
        render(root / f"{slug}.ts",
               "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=15",
               "-f", "lavfi", "-i", f"sine=frequency={frequency(slug)}:sample_rate=44100",
               "-t", str(seconds), "-c:v", "libx264", "-profile:v", "baseline", "-preset", "veryfast",
               "-b:v", "200k", "-g", "30", "-c:a", "aac", "-b:a", "64k", "-f", "mpegts")
        cover(root / f"{slug}.png", slug)
        lines.append(f'#EXTINF:-1 tvg-id="{channel["id"]}" tvg-chno="{channel["number"]}" '
                     f'tvg-name="{channel["name"]}" tvg-logo="{MEDIA_HOST}/{slug}.png",{channel["name"]}')
        lines.append(f"{MEDIA_HOST}/{slug}.ts")
    (root / "channels.m3u").write_text("\n".join(lines) + "\n", encoding="utf-8")
    log(f"livetv: {len(fixtures['channels'])} channels")


def guide(fixtures, root):
    """The XMLTV guide: back-to-back programmes from a day ago to two weeks ahead."""
    fmt = "%Y%m%d%H%M%S +0000"
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    xml = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="vault-test-server">']
    for channel in fixtures["channels"]:
        xml.append(f'  <channel id="{escape(channel["id"])}"><display-name>{escape(channel["name"])}</display-name></channel>')
    for channel in fixtures["channels"]:
        start, n = now - GUIDE_PAST, 0
        show = channel["name"].split("|")[-1]
        while start < now + GUIDE_FUTURE:
            n += 1
            xml.append(f'  <programme start="{start.strftime(fmt)}" stop="{(start + GUIDE_SLOT).strftime(fmt)}" '
                       f'channel="{escape(channel["id"])}"><title>{escape(show)} Programme {n}</title>'
                       f'<desc>A generated test programme.</desc></programme>')
            start += GUIDE_SLOT
    xml.append("</tv>")
    (root / "guide.xml").write_text("\n".join(xml) + "\n", encoding="utf-8")
    log(f"guide: {now - GUIDE_PAST:%Y-%m-%d} to {now + GUIDE_FUTURE:%Y-%m-%d}")


def empty(folder, keep=()):
    """Deletes what's in [folder] but not the folder itself, and only empties the folders in [keep]:
    the compose services bind-mount them, and a running container's mount keeps pointing at the
    deleted folder, not at one made again in its place."""
    for child in folder.iterdir():
        if child.name in keep:
            empty(child)
        elif child.is_dir() and not child.is_symlink():
            shutil.rmtree(child)
        else:
            child.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=HERE / ".data")
    data = parser.parse_args().out
    fixtures_file = HERE / "fixtures.json"
    fixtures = json.loads(fixtures_file.read_text(encoding="utf-8"))
    media = data / "media"

    stamp = hashlib.sha256(fixtures_file.read_bytes() + Path(__file__).read_bytes()).hexdigest()
    stamp_file = media / ".stamp"
    if media.exists() and (not stamp_file.exists() or stamp_file.read_text() != stamp):
        log("fixtures changed; rebuilding media")
        empty(media, keep={"livetv"})  # docker-compose.yml mounts media/ and media/livetv/
    music(fixtures["music"], media / "music")
    audiobooks(fixtures["audiobooks"], media / "audiobooks")
    books(fixtures["books"], media / "books", data / "downloads")
    livetv(fixtures["livetv"], media / "livetv")
    stamp_file.write_text(stamp)
    guide(fixtures["livetv"], media / "livetv")


if __name__ == "__main__":
    main()
