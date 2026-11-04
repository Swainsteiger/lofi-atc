"""Lofi ATC on the PC: serves this folder at http://127.0.0.1:8770 and opens it as a Chrome app window.
Also answers /activity with how many planes are around each airport right now (from adsb.fi's open data),
which a page opened straight from disk could not fetch itself. Started by the desktop shortcut with pythonw
(no console window); quits on its own once the page has been closed for 10 minutes."""
import http.server
import json
import math
import os
import socket
import subprocess
import threading
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PORT = 8770
URL = "http://127.0.0.1:%d/" % PORT
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
REFRESH_S = 300
IDLE_EXIT_S = 600
# A plane counts toward an airport within this distance and below this altitude (or taxiing):
# the traffic its tower and approach controllers are talking to.
RADIUS_NM, CEILING_FT = 20, 10000

activity = {"updated": 0, "counts": {}}
last_seen = time.time()


def count_planes(lat, lon):
    url = "https://opendata.adsb.fi/api/v2/lat/%.3f/lon/%.3f/dist/%d" % (lat, lon, RADIUS_NM)
    req = urllib.request.Request(url, headers={"User-Agent": "LofiATC/1.0 (personal ambient player)"})
    planes = json.load(urllib.request.urlopen(req, timeout=15)).get("aircraft", [])
    # On the ground only while taxiing: parked planes often leave their transponders on.
    return sum(1 for p in planes if (p.get("alt_baro") == "ground" and (p.get("gs") or 0) > 5)
               or (isinstance(p.get("alt_baro"), (int, float)) and p["alt_baro"] < CEILING_FT))


def refresher():
    airports = json.load(open(os.path.join(HERE, "..", "airports.json"), encoding="utf-8"))
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
        if self.path.startswith("/activity"):
            body = json.dumps(activity).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            super().do_GET()

    def log_message(self, *a):
        pass


def open_window():
    if os.environ.get("LOFIATC_NO_WINDOW"):  # testing
        return
    if os.path.exists(CHROME):
        subprocess.Popen([CHROME, "--app=" + URL, "--window-size=720,960"])
    else:
        import webbrowser
        webbrowser.open(URL)


def main():
    # Already running (window closed and reopened): just open another window.
    with socket.socket() as s:
        if s.connect_ex(("127.0.0.1", PORT)) == 0:
            open_window()
            return
    server = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    threading.Thread(target=refresher, daemon=True).start()
    open_window()
    while time.time() - last_seen < IDLE_EXIT_S:
        time.sleep(30)
    server.shutdown()


if __name__ == "__main__":
    main()
