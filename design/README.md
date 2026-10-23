# Design history and tools

How the home screen got its look, and the scripts that made its assets. Full write-up with reasoning and screenshots: beacon2 `/notes/sony-a9g-home-screen.html`.

## mockups/

HTML at 1920×1080, rendered with headless Chrome:
`"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --window-size=1920,1080 --virtual-time-budget=5000 --screenshot=out.png file://$PWD/<file>.html`

- `round1/`: Faceplate, Row, Guide (stand-in logos). Rejected as "too minimalist and ugly".
- `round2/`: Receiver (walnut and brushed metal), Bento, Letterboard. Rejected as "too overly stylized".
- `round3/`: Spotlight, Type, Dock. **Type won**; `gen_type.py` generates its pages.
- `weather/`: options A, B, C (`gen.py`) and the chosen D (`gen_d.py`, from the design-review agent), which was later adjusted on the TV.
- `img/`: the real app banners and icons pulled from the TV's APKs, used by the mock-ups.

Some HTML files reference assets by relative path (`img/`, `big_sur.jpg`, `youtube.png`); copy them next to the file before rendering.

## renders/

WebP snapshots of the mock-ups and real TV screenshots (`real-*`, `v3-*`).

## tools/

- `photos/`: picks landscape photos from Wikimedia Commons' Featured and Quality collections. `commons_search.py subjects.json candidates.json` searches (throttled for Commons' rate limits); `thumbs.py` builds a contact sheet; `fetch_final.py index.json "key#n,…" rawdir outdir` downloads, crops to 1920×1080 and writes `photos.json` with credits. `alternates.json` lists the four aircraft and animal options not used.
- `banners/`: `banner2.py` and `icon.py` extract an app's TV banner or icon from pulled APKs (`adb shell pm path <pkg>` then `adb pull` base and split APKs). Needs `androguard==4.1.2` in a venv.
- `weather-probe/`: a throwaway app that measured the weather feature's cost on the TV (location read + three HTTPS fetches: 0.97 s first, 0.19 s after, +0.9 MB). Build it like the main app with its own manifest.

`../spec-v0.md` is the first technical spec, before the adversarial review.
