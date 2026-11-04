"""Builds the "Soft lofi" playlist from music/source.json: Creative Commons tracks (CC BY / CC BY-SA, no
NoDerivatives or NonCommercial) picked from free-stock-music.com's lo-fi catalog.

The site only serves its MP3s through its own pages, so first download each track with its "Free Download"
button into music/downloads (file names as in source.json's mp3 links). This script then converts each one to
128 kbps so it streams quickly on a TV (a format change, which both licenses allow) and tags it with title,
artist and license. Writes music/build/*.mp3 (uploaded as the GitHub release "music-v1"), tracks.json (the
playlist the PC page and TV app read), and MUSIC-CREDITS.md (the attribution the licenses require).
Needs ffmpeg. Usage: python music/build_music.py [path to ffmpeg]"""
import json
import os
import re
import subprocess
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DOWNLOADS = os.path.join(HERE, "downloads")
BUILD = os.path.join(HERE, "build")
RELEASE = "https://github.com/Swainsteiger/lofi-atc/releases/download/music-v1/"
FFMPEG = sys.argv[1] if len(sys.argv) > 1 else "ffmpeg"
LICENSE_URL = {
    "CC BY 3.0": "https://creativecommons.org/licenses/by/3.0/",
    "CC BY 4.0": "https://creativecommons.org/licenses/by/4.0/",
    "CC BY-SA 3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
}


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def seconds(path):
    out = subprocess.run([FFMPEG, "-i", path], capture_output=True, text=True).stderr
    h, m, s = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", out).groups()
    return round(int(h) * 3600 + int(m) * 60 + float(s))


def main():
    os.makedirs(BUILD, exist_ok=True)
    source = json.load(open(os.path.join(HERE, "source.json"), encoding="utf-8"))
    tracks = []
    for i, t in enumerate(source, 1):
        assert t["license"] in LICENSE_URL, t
        name = slug(t["artist"].split(" feat.")[0].replace("&", "and")) + "-" + slug(t["title"]) + ".mp3"
        out = os.path.join(BUILD, name)
        if not os.path.exists(out):
            raw = os.path.join(DOWNLOADS, t["mp3"].split("/")[-1])
            subprocess.run([FFMPEG, "-v", "error", "-y", "-i", raw, "-map", "0:a", "-codec:a", "libmp3lame",
                            "-b:a", "128k", "-map_metadata", "-1",
                            "-metadata", "title=" + t["title"], "-metadata", "artist=" + t["artist"],
                            "-metadata", "comment=%s, %s, via free-stock-music.com" % (t["license"], t["site"]),
                            out], check=True)
        tracks.append({"title": t["title"], "artist": t["artist"], "seconds": seconds(out),
                       "license": t["license"], "licenseUrl": LICENSE_URL[t["license"]],
                       "artistSite": t["site"], "source": t["page"], "url": RELEASE + name})
        print("%2d/%d %s" % (i, len(source), name), flush=True)

    with open(os.path.join(HERE, "tracks.json"), "w", encoding="utf-8") as f:
        json.dump(tracks, f, ensure_ascii=False, indent=1)

    lines = ["# Music credits", "",
             "The **Soft lofi** playlist is made of these tracks, each under a Creative Commons license that allows",
             "sharing with credit. They were converted from 320 to 128 kbps MP3 for streaming and are otherwise unchanged.",
             "Found via [free-stock-music.com](https://www.free-stock-music.com).", ""]
    for t in tracks:
        lines.append("- **%s** by %s (%s) — [%s](%s) — [source](%s)" % (
            t["title"], t["artist"], t["artistSite"], t["license"], t["licenseUrl"], t["source"]))
    with open(os.path.join(ROOT, "MUSIC-CREDITS.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    total = sum(os.path.getsize(os.path.join(BUILD, os.path.basename(t["url"]))) for t in tracks)
    print("%d tracks, %d min, %.0f MB" % (len(tracks), sum(t["seconds"] for t in tracks) // 60, total / 1e6))


if __name__ == "__main__":
    main()
