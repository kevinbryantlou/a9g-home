# Custom home screen for Kevin's Sony XBR-55A9G — draft technical spec (v0)

## Goal
Replace the stock Android TV Home (com.google.android.tvlauncher 7.7.15, 150–334 MB PSS, cold start 1.62 s after tuning, janky for the first seconds after a cold start) with a purpose-built home screen that is (1) tiny in memory, (2) near-instant on cold start, (3) shows only what Kevin uses, and (4) is fully reversible.

## Device facts (measured over ADB, 22 Oct 2026)
- Android TV 10 (API 29), MediaTek MT5893, 4× Cortex-A73 pinned at 1.5 GHz (performance governor), 2.3 GB RAM, ~550–630 MB free idle, 100 MB zram swap usually full.
- 1920×1080 UI at density 320 (xhdpi), 60 Hz UI.
- `cmd package set-home-activity [--user N] COMPONENT` exists. HOME currently resolves to com.google.android.tvlauncher/.MainActivity (priority 2); com.android.tv.settings/.system.FallbackHome is the priority −1000 fallback.
- Stock launcher depends on com.google.android.tvrecommendations (disabling it slowed launcher cold start 2.5 → 5.8 s). Irrelevant if the stock launcher is no longer HOME, but it stays installed and enabled.
- Usage (yearly log): Netflix 51 h, Sony TV app/HDMI (PS4) 33 h, YouTube 22 h, Prime Video 8 h, AirPlay 4 h (initiated from phone — no tile needed), Assistant 2 h (remote mic button), Spotify 0.3 h.
- Leanback-launchable apps now: Play Store, YouTube, Sony Music (osat.music), Sony "Media Player" (smartmediaapp), Sony TV app (tvx), Prime Video, Netflix, Sony Notification Center, Sony smart-speaker setup (seeds.iot), Sony Help, Sony Timers, Spotify. Plus Settings (com.android.tv.settings).
- HDMI-CEC: PlayStation 4 on HDMI 2 (physical 0x2000, logical 4); Sonos Beam on HDMI 3 (eARC, 0x3000). TV input ids: com.sony.dtv.tvinput.external/.ExternalTvInputService/HW1..HW5 and /HDMI200004 (CEC device id for the PS4).
- Input switching: com.sony.dtv.tvx/.MainActivity handles ACTION_VIEW with type vnd.android.cursor.item/channel, i.e. TvContract.buildChannelUriForPassthroughInput(inputId). UNVERIFIED which input id the PS4 needs (HW2 vs HDMI200004) — test during build.
- TvProvider: READ_EPG_DATA is protection "normal"; ACCESS_ALL_EPG_DATA and ACCESS_WATCHED_PROGRAMS are signature|privileged → a third-party home screen cannot read other apps' Watch Next / Continue Watching. No "continue watching" row in v1.

## Scope v1
- One primary row of 4 large tiles: Netflix, YouTube, Prime Video, PlayStation (switches to HDMI 2).
- One secondary row of small tiles: Spotify, Settings, Inputs (HDMI 1–4 picker; launches the passthrough URI), All apps (simple grid of every LEANBACK_LAUNCHER activity, so nothing is ever unreachable), Stock home (explicitly launches com.google.android.tvlauncher/.MainActivity — escape hatch).
- Header: time + date. Nothing else.
- Tile art: each app's own leanback banner via PackageManager.getApplicationBanner / ActivityInfo.loadBanner (no bundled logos, no network). PlayStation tile drawn in-app (no banner exists for an HDMI input).
- No network access, no INTERNET permission, no background service, no boot receiver, no notifications, no Watch Next.
- D-pad only. Focus state must be legible from 3 m. Back on home does nothing. Mic/Assistant button is handled by the system (katniss) — UNVERIFIED that it works identically with a third-party HOME.

## Implementation
- Single-Activity app, package com.beacon2.home (placeholder), Java, plain android.view / android.widget — no AndroidX, no Compose, no Leanback library — to keep APK small and cold start minimal. minSdk 29, targetSdk 29.
- Manifest: HomeActivity with MAIN + HOME + DEFAULT categories, launchMode singleTask, stateNotNeeded, excludeFromRecents, screenOrientation landscape, theme with no window animation preview flicker (windowDisablePreview false, black windowBackground).
- Banners loaded once on create, cached in memory; refreshed on PACKAGE_ADDED/REMOVED/CHANGED via a receiver registered while the activity is alive.
- Build on the Mac: Homebrew openjdk@17 + android-commandlinetools (sdkmanager: platform-tools already present, build-tools;34.0.0, platforms;android-29) + Gradle wrapper; debug-signed APK; `adb install`.
- Make default: `adb shell cmd package set-home-activity com.beacon2.home/.HomeActivity`.

## Reversibility
- Revert to stock: `adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity`, or simply uninstall com.beacon2.home (HOME falls back to the stock launcher automatically).
- The "Stock home" tile opens the stock launcher without changing the default.
- Stock launcher and tvrecommendations remain installed and enabled.

## Success criteria (same tests as the tune-up brief)
- Cold start to first frame < 500 ms (stock: 1.62 s).
- PSS < 40 MB (stock: 150–334 MB).
- D-pad navigation janky frames < 5% both warm and right after cold start (stock: 3.6% warm / 30% after cold start).
- After setting default: confirm stock launcher process is no longer started at boot or after HOME; measure free RAM.

## Known open questions
1. Which input id switches to the PS4 (HW2 vs HDMI200004), and does tvx show its own overlay?
2. Does a Play Store update to the stock launcher clear the preferred HOME, silently reverting to stock?
3. Does the remote's mic button still reach Assistant with a third-party HOME?
4. Does anything else (Sony TV app "Home" behaviour, Sony Notification Center, CEC one-touch-play) assume the stock launcher?
5. If USB debugging is turned off afterwards, the only revert path without ADB is the "Stock home" tile (which opens it but does not make it default) or uninstalling via Settings → Apps.
