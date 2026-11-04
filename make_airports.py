"""Builds the shared data for the PC page and the TV app:
  airports.json  airports with their LiveATC feeds, from feed lists saved from lofiatc.com/airport/<icao>.
                 Each feed plays from LiveATC's public address d.liveatc.net/<mount>, the one its .pls playlists
                 give media players.
  stations.json  the music: the Soft lofi playlist first (its tracks are music/tracks.json, built by
                 music/build_music.py), then the ad-free radio stations (STATIONS below).
All are also written as pc/data.js (browsers block reading files from disk) and copied into the TV app's assets.
Usage: python make_airports.py [folder with <icao>.json files]   (without it, airports.json is kept as is)"""
import glob
import json
import os
import shutil
import sys

# Shown first in this order; the rest follow alphabetically.
FIRST = ["KLAS", "KSJC", "KSFO", "KOAK"]
# Preferred default feed, by words in its name.
PREFER = ["tower", "twr", "approach", "app", "departure", "dep", "ground", "gnd"]
# For the airport's local clock.
TZ = {"KLAS": "America/Los_Angeles", "KSJC": "America/Los_Angeles", "KSFO": "America/Los_Angeles",
      "KOAK": "America/Los_Angeles", "KLAX": "America/Los_Angeles", "KSAN": "America/Los_Angeles",
      "KPHX": "America/Phoenix", "KDEN": "America/Denver", "KDFW": "America/Chicago", "KORD": "America/Chicago",
      "KATL": "America/New_York", "KBOS": "America/New_York", "KJFK": "America/New_York", "CYYZ": "America/Toronto",
      "EHAM": "Europe/Amsterdam", "RJTT": "Asia/Tokyo", "YSSY": "Australia/Sydney"}
# Airport reference points, for counting the planes around each one (how busy its radio is likely to be).
POS = {"KLAS": (36.084, -115.154), "KSJC": (37.363, -121.929), "KSFO": (37.619, -122.375),
       "KOAK": (37.721, -122.221), "CYYZ": (43.677, -79.631), "EHAM": (52.310, 4.768), "KATL": (33.640, -84.427),
       "KBOS": (42.364, -71.005), "KDEN": (39.856, -104.674), "KDFW": (32.897, -97.038), "KJFK": (40.641, -73.778),
       "KLAX": (33.942, -118.408), "KORD": (41.978, -87.905), "KPHX": (33.434, -112.012), "KSAN": (32.734, -117.190),
       "RJTT": (35.549, 139.780), "YSSY": (-33.946, 151.177)}
# Ad-free only: Radio Caprice runs commercial-free genre channels; Nightride FM, Nightwave Plaza and
# Radio Paradise are listener-supported. Lofi first.
STATIONS = [
    ("Lofi", "Radio Caprice · Lo-Fi", "http://79.111.14.76:8002/lo-fi"),
    ("Lofi", "Radio Caprice · Lo-Fi (2)", "http://79.111.14.76:8004/lo-fi"),
    ("Lofi", "Radio Caprice · Trip-Hop", "http://79.111.14.76:8002/triphop"),
    ("Chill", "Radio Caprice · Downtempo", "http://79.120.77.11:8004/downtempo"),
    ("Chill", "Radio Caprice · Chill Out", "http://79.111.14.76:8000/chillout"),
    ("Chill", "Radio Caprice · Lounge", "http://79.111.14.76:8002/lounge"),
    ("Chill", "Radio Caprice · New Age", "http://79.120.77.11:8002/newage"),
    ("Synth", "Nightride FM · Chillsynth", "https://stream.nightride.fm/chillsynth.mp3"),
    ("Synth", "Nightride FM · Datawave", "https://stream.nightride.fm/datawave.mp3"),
    ("Synth", "Nightride FM · Spacesynth", "https://stream.nightride.fm/spacesynth.mp3"),
    ("Synth", "Nightwave Plaza (vaporwave)", "https://radio.plaza.one/mp3"),
    ("Mellow", "Radio Paradise · Mellow Mix", "https://stream.radioparadise.com/mellow-128"),
    ("Mellow", "Radio Paradise · Main Mix", "https://stream.radioparadise.com/mp3-128"),
]


def rank(name):
    n = name.lower()
    if "atis" in n:
        return 99
    if any(w in n for w in ("helicopter", "discrete", "emergency")):
        return 60
    for i, w in enumerate(PREFER):
        if w in n:
            return i
    return 50


here = os.path.dirname(os.path.abspath(__file__))
airports = []
for f in glob.glob(os.path.join(sys.argv[1], "*.json")) if len(sys.argv) > 1 else []:
    try:
        a = json.load(open(f, encoding="utf-8"))
    except ValueError:
        continue
    feeds = [{"name": v["name"], "url": "https://d.liveatc.net/" + k} for k, v in a.get("feeds", {}).items()]
    if not feeds:
        continue
    feeds.sort(key=lambda x: (rank(x["name"]), x["name"]))
    airports.append({"icao": a["icao"], "name": a["name"], "city": a.get("city") or "", "feeds": feeds})
if airports:
    airports.sort(key=lambda a: (FIRST.index(a["icao"]) if a["icao"] in FIRST else 99, a["icao"]))
    for a in airports:
        a["tz"] = TZ.get(a["icao"], "")
        a["lat"], a["lon"] = POS[a["icao"]]
    json.dump(airports, open(os.path.join(here, "airports.json"), "w", encoding="utf-8"), indent=1)
else:
    airports = json.load(open(os.path.join(here, "airports.json"), encoding="utf-8"))
# The Soft lofi playlist comes first: the default music, and the only one this project hosts itself.
tracks = json.load(open(os.path.join(here, "music", "tracks.json"), encoding="utf-8"))
stations = [{"group": "Soft lofi", "name": "Soft lofi (%d tracks, shuffled)" % len(tracks), "playlist": True}]
stations += [{"group": g, "name": n, "url": u} for g, n, u in STATIONS]

json.dump(stations, open(os.path.join(here, "stations.json"), "w", encoding="utf-8"), indent=1)
with open(os.path.join(here, "pc", "data.js"), "w", encoding="utf-8") as f:
    f.write("window.AIRPORTS = " + json.dumps(airports) + ";\n")
    f.write("window.STATIONS = " + json.dumps(stations) + ";\n")
    f.write("window.TRACKS = " + json.dumps(tracks, ensure_ascii=False) + ";\n")
assets = os.path.join(here, "tv", "app", "src", "main", "assets")
for name in ("airports.json", "stations.json"):
    shutil.copy(os.path.join(here, name), assets)
shutil.copy(os.path.join(here, "music", "tracks.json"), assets)
print(len(airports), "airports,", sum(len(a["feeds"]) for a in airports), "feeds,", len(stations), "stations,",
      len(tracks), "playlist tracks")
