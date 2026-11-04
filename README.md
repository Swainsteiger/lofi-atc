# Lofi ATC

Ad-free lofi music with live air traffic control radio on top, for a PC browser, an Android TV, or any
screen on your home network. Inspired by lofiatc.com, which went offline.

![TV app](docs/tv-app.png)

## What it does

- **Live tower and approach radio** from 17 airports (Las Vegas, San Francisco, San Jose, New York,
  Amsterdam, Tokyo, Sydney and more) via [LiveATC.net](https://www.liveatc.net).
- **13 ad-free music stations**: lofi, trip-hop, downtempo, chillsynth, vaporwave and mellow mixes.
- **Busiest airport finder**: counts the planes in the air or taxiing within 20 nm of each airport
  ([adsb.fi](https://adsb.fi) open data), so you can jump to the one with the most radio chatter.
- **Talk light** that glows while a controller or pilot is speaking.
- Separate volume for radio and music. A dead radio channel is skipped automatically.

## PC (any browser)

Needs Python 3 (standard library only).

```
python pc/serve.pyw
```

This serves the page at `http://127.0.0.1:8770`, opens it in Chrome as an app window (or your default
browser), and fetches the plane counts that a browser can't fetch on its own. It quits ten minutes
after the last page is closed.

Other devices on the same Wi-Fi (a tablet, an old smart display) can open
`http://<your-pc-ip>:8770` in their browser. Windows may need an inbound firewall rule for TCP 8770.

Keyboard: Space play/pause, ←/→ airport, ↑/↓ radio channel, M music, B busiest airport.

On Windows, a desktop shortcut that runs `pythonw.exe pc\serve.pyw` (with `pc\lofiatc.ico` as its icon)
starts it without a console window.

## Android TV

![Busiest airport on the TV](docs/tv-busiest.png)

An Android TV app (Android 8+), built with Gradle and ExoPlayer:

```
cd tv
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

It shows up as **Lofi ATC** in the TV's apps. On the remote: ▲▼ choose a row, ◀▶ change it, OK
play/pause, CH+/CH− airport. OK on **Busiest now** jumps to the busiest airport. The release build is
signed with the debug key, for sideloading.

## Changing airports or stations

The lists live in `make_airports.py` (`STATIONS` for music, `POS` and `TZ` for airport location and
time zone). The airport feed lists come from lofiatc.com's `/airport/<icao>` JSON; save one per airport
into a folder, then run

```
python make_airports.py <folder>
```

It rewrites `airports.json`, `stations.json`, `pc/data.js` and the TV app's assets. Rebuild the TV app
afterwards. `make_icon.py` redraws the icon and TV banner (needs Pillow).

## Credits and fair use

- ATC audio: [LiveATC.net](https://www.liveatc.net). Their streams are offered for personal listening;
  this project plays them in your own browser or app and does not rebroadcast them. It is not
  affiliated with or endorsed by LiveATC.net or lofiatc.com.
- Plane counts: [adsb.fi](https://adsb.fi) open data, at most one request a second.
- Music: [Radio Caprice](http://radcap.ru), [Nightride FM](https://nightride.fm),
  [Nightwave Plaza](https://plaza.one) and [Radio Paradise](https://radioparadise.com), all
  commercial-free. Support them if you enjoy them.
- Font: [Outfit](https://github.com/Outfitio/Outfit-Fonts), SIL Open Font License 1.1.

Code: MIT, see `LICENSE`. The Outfit font files are not covered by it; they are under the SIL Open Font
License 1.1, see `FONT-LICENSE-OFL.txt`.
