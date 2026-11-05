"""Lofi ATC on the PC: serves this folder at http://127.0.0.1:8770 and opens it as a Chrome app window.
Also answers /activity with how many planes are around each airport right now (from adsb.fi's open data),
which a page opened straight from disk could not fetch itself. Started by the desktop shortcut with pythonw
(no console window), or as LofiATC.exe from the Windows installer; quits on its own once the page has been
closed for 10 minutes. Pass --lan to let other devices on the home network open it too."""
import http.server
import json
import os
import re
import socket
import subprocess
import sys
import threading
import time
import urllib.request

if getattr(sys, "frozen", False):  # packaged LofiATC.exe: its files are unpacked next to the bundled Python
    HERE = os.path.join(sys._MEIPASS, "pc")
    AIRPORTS = os.path.join(sys._MEIPASS, "airports.json")
else:
    HERE = os.path.dirname(os.path.abspath(__file__))
    AIRPORTS = os.path.join(HERE, "..", "airports.json")
PORT = 8770
# The version (VERSION in index.html) goes into the page's address and its data file's, so a browser that kept
# a copy of an older version's page can't show that one after an update.
with open(os.path.join(HERE, "index.html"), encoding="utf-8") as _f:
    VERSION = (re.search(r'const VERSION = "([^"]+)"', _f.read()) or [None, "dev"])[1]
URL = "http://127.0.0.1:%d/?v=%s" % (PORT, VERSION)
# An app window (no tabs or address bar) in Chrome, or in Edge, which every Windows PC has.
BROWSERS = [os.path.expandvars(p) for p in (
    r"%ProgramFiles%\Google\Chrome\Application\chrome.exe",
    r"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe",
    r"%LocalAppData%\Google\Chrome\Application\chrome.exe",
    r"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe",
    r"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe")]
LAN = "--lan" in sys.argv
REFRESH_S = 300
IDLE_EXIT_S = 600
# A plane counts toward an airport within this distance and below this altitude (or taxiing):
# the traffic its tower and approach controllers are talking to.
RADIUS_NM, CEILING_FT = 20, 10000

activity = {"updated": 0, "counts": {}}
last_seen = time.time()
quitting = threading.Event()


def count_planes(lat, lon):
    url = "https://opendata.adsb.fi/api/v2/lat/%.3f/lon/%.3f/dist/%d" % (lat, lon, RADIUS_NM)
    req = urllib.request.Request(url, headers={"User-Agent": "LofiATC/1.0 (personal ambient player)"})
    planes = json.load(urllib.request.urlopen(req, timeout=15)).get("aircraft", [])
    # On the ground only while taxiing: parked planes often leave their transponders on.
    return sum(1 for p in planes if (p.get("alt_baro") == "ground" and (p.get("gs") or 0) > 5)
               or (isinstance(p.get("alt_baro"), (int, float)) and p["alt_baro"] < CEILING_FT))


def refresher():
    airports = json.load(open(AIRPORTS, encoding="utf-8"))
    while True:
        for a in airports:
            try:
                # Published one by one, so the first counts show within seconds of opening.
                activity["counts"] = dict(activity["counts"], **{a["icao"]: count_planes(a["lat"], a["lon"])})
                activity["updated"] = int(time.time())
            except Exception:
                pass
            time.sleep(1.5)  # adsb.fi asks for at most one request a second
        time.sleep(REFRESH_S)


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=HERE, **k)

    def do_GET(self):
        global last_seen
        last_seen = time.time()
        path = self.path.split("?")[0]
        if path.startswith("/activity"):
            self.send(json.dumps(activity).encode(), "application/json")
        elif path == "/version":
            self.send(VERSION.encode(), "text/plain")
        elif path == "/quit":  # a newer version taking over asks this one to step aside
            self.send(b"bye", "text/plain")
            quitting.set()
        elif path in ("/", "/index.html"):
            with open(os.path.join(HERE, "index.html"), encoding="utf-8") as f:
                page = f.read().replace('src="data.js"', 'src="data.js?v=%s"' % VERSION)
            self.send(page.encode("utf-8"), "text/html; charset=utf-8")
        else:
            super().do_GET()

    def send(self, body, kind):
        self.send_response(200)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        # Browsers keep old copies of the page otherwise, so an updated install could still show the old version.
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()

    def translate_path(self, path):
        # For testing a playlist before it is uploaded (page opened with ?music=local): /music/<file> comes
        # from music/build. Only there when running from the source folder.
        if path.startswith("/music/"):
            return os.path.join(HERE, "..", "music", "build", os.path.basename(path.split("?")[0]))
        return super().translate_path(path)

    def log_message(self, *a):
        pass


def open_window():
    if os.environ.get("LOFIATC_NO_WINDOW"):  # testing
        return
    for exe in BROWSERS:
        if os.path.exists(exe):
            subprocess.Popen([exe, "--app=" + URL, "--window-size=720,960"])
            return
    import webbrowser
    webbrowser.open(URL)


def running_version():
    """The version of the Lofi ATC already running here, "" if it's too old to say, None if none is."""
    with socket.socket() as s:
        if s.connect_ex(("127.0.0.1", PORT)) != 0:
            return None
    try:
        return urllib.request.urlopen("http://127.0.0.1:%d/version" % PORT, timeout=3).read().decode().strip()
    except Exception:
        return ""


def main():
    running = running_version()
    if running and running != VERSION:
        # Another version (1.1.3 or later) is still running: ask it to quit, then take over. (The installer stops
        # older ones. Stopping another program ourselves looks like malware to antivirus programs.)
        try:
            urllib.request.urlopen("http://127.0.0.1:%d/quit" % PORT, timeout=3).read()
        except Exception:
            pass
        for _ in range(40):
            time.sleep(0.25)
            if running_version() is None:
                break
        running = running_version()
    if running is not None:  # already running (window closed and reopened): just open another window
        open_window()
        return
    server = http.server.ThreadingHTTPServer(("0.0.0.0" if LAN else "127.0.0.1", PORT), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    threading.Thread(target=refresher, daemon=True).start()
    open_window()
    while time.time() - last_seen < IDLE_EXIT_S and not quitting.is_set():
        quitting.wait(30)
    server.shutdown()
    server.server_close()  # free the port right away for the version taking over


if __name__ == "__main__":
    main()
