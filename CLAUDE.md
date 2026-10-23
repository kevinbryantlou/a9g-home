# A9G Home

A home screen app for one TV: Kevin's **Sony XBR-55A9G** (2019 OLED, Android TV 10 / API 29, MediaTek MT5893, 2.3 GB RAM). It's the TV's default Home. Write-ups live on beacon2 (gated `/notes`): `/notes/sony-a9g-home-screen.html` (this app: design, measurements, reviews, recovery card) and `/notes/sony-a9g-tune-up.html` (the debloat before it).

## The TV

- `192.168.86.171` on Google/Nest Wifi. PS4 Pro on HDMI 2, Sonos Beam on HDMI 3 (eARC). A Nest Hub turns the TV on and off.
- Remote keys: Input (Sony's input picker), Home, APPS (`KEYCODE_ALL_APPS`, which Android routes only to a system home, so it does nothing now). **No Menu key.**
- **Network ADB is off** (since 2026-10-22). To change anything, Kevin turns on Settings → Device Preferences → Developer options → USB debugging, then `adb connect 192.168.86.171:5555`.
- Ask Kevin to leave the remote alone during scripted test runs; results gathered while he's using it are unreliable.

## System changes on the TV (reversible)

- **Disabled**: `com.google.android.tvlauncher` (Google's home; its HOME filter has system priority 2, which always beats an installed app's 0, so disabling it is the only way to be Home) and `com.google.android.tvrecommendations` (Android TV Core Services; only fed Google's home).
- Earlier debloat (`pm disable-user` / uninstall): Samba TV, LivingFit, Sony Select, promos, demo mode, Play Games, QuickSet, DLNA, hotel/pro mode, Apple TV, YouTube Music, Google TV, GeForce NOW, Disney+, Hulu, Max, Viki.
- **Never disable**: `com.vewd.core.service` (AirPlay binds it), `com.sony.dtv.panelsettings` (OLED panel refresh), `com.google.android.katniss` (voice), tvx / tvinput / tvapi, braviasync (HDMI-CEC), `com.android.tv.settings`.
- If Google's home reappears, an update re-enabled it: `adb shell pm disable-user --user 0 com.google.android.tvlauncher`.
- To restore Google's home: enable **Core Services first**, then Android TV Home. Never uninstall this app while tvlauncher is disabled (Home becomes a blank FallbackHome).

## Build and deploy

- `./deploy.sh`: runs `build.sh` (aapt2 → javac → d8 → zipalign → apksigner, no Gradle), installs over ADB, AOT-compiles (`cmd package compile -m speed`), grants coarse location, and prints the HOME role holder.
- Toolchain: Homebrew `openjdk@17`, `android-commandlinetools` with `platforms;android-29` and `build-tools;34.0.0`.
- Signing: `~/.a9g-home/release.jks` + `storepass`, backed up in `signing/` (committed deliberately; the repo is private). On a new Mac: `mkdir -p ~/.a9g-home && cp signing/* ~/.a9g-home/`.
- Versions: versionCode = git commit count; builds from a dirty tree get `-dirty` in the name. Deploy from a clean commit.
- **Never rename `.HomeActivity`**: Android drops the HOME preference when the HOME component set changes.
- Adding a permission in an update can need a TV reboot before it takes effect (INTERNET gave `EPERM` until a reboot).

## Code map (`src/com/beacon2/home/`)

- `HomeActivity`: the "Type" home screen. Five 150 px rows (YouTube, Prime Video, Netflix, PS4 → HDMI 2 input `HW2`, Spotify), banner on the right, buttons (Inputs, All apps, Settings). Focus is handled by hand in `dispatchKeyEvent`, because rows move with translationY and Android's focus search ignores transforms. Also: idle mode (45 s; menu fades, photo up), night mode (20:00–06:30), ±4 px burn-in shift every minute, safe mode.
- `AllAppsActivity`: an A–Z list of every TV app, ending with "Restore stock home".
- `Slideshow`: 20 bundled 1920×1080 photos (`assets/photos/`, credits in `photos.json`), RGB_565, one every 60 s, decoded off-thread, old bitmaps recycled.
- `Weather`: Open-Meteo (no key), at most hourly, coarse network location with a Pasadena fallback, cached; hidden when older than 2 h.
- `CrashGuard`: a crash within the last 60 s makes the next launch show a plain fallback screen rather than looping (TVs show no crash dialog, and the HOME role keeps relaunching us).
- `Sources`, `Ui`, `FlatImageView`: launch intents and banners; type and colour helpers; a fade without an offscreen buffer.

## Design rules (Kevin's calls)

- "Type" layout: names are the menu, one 30% neutral shade plus a left gradient (×1.5 at night), **no text shadows, no per-app colour washes, no usage stats or "last opened"**.
- Right column: time 128 px leads; temperature 64 px; date, caption and forecast all 26 px, ranked by opacity (caption and forecast 85%, date 60%, credit 50% in both states). Forecast sits on the button row's baseline above the credit. Weather keeps the same position in idle.
- Rejected directions and the reasoning: `design/README.md`. Avoid anything that looks AI-generated (glows, luxury serifs, tracked uppercase eyebrows, ornament dividers).

## Known open items

- Once (twice, unreproduced) the YouTube row opened Netflix, on the first launch after waking from the screensaver.
- Steady memory is ~50 MB (GPU caches the previous photo) against a 40 MB target; stock was 150–331 MB.
