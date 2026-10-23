# A9G Home

A home screen app for one TV: Kevin's Sony XBR-55A9G (Android TV 10). Plain Java, no libraries, built without Gradle.

- **Home**: the service names are the menu (YouTube, Prime Video, Netflix, PS4 on HDMI 2, Spotify), over a photo slideshow behind a single neutral shade. Inputs opens Sony's input picker; All apps lists every TV app.
- **Default home**: Google's launcher (`com.google.android.tvlauncher`) is *disabled*. It is a system app whose HOME filter has priority 2, which always beats an installed app's 0, so disabling it is the only way for this app to be what the Home button opens.
- **Ways back to Google's home screen**: All apps → Restore stock home (or long-press Menu) opens Google launcher's app-info page; choose **Enable**, then press Home. Over ADB: `adb shell pm enable com.google.android.tvlauncher`. To uninstall this app, enable Google's launcher *first*, or the TV is left with only the boot placeholder.
- **If the home screen fails to build** it shows a plain fallback with Settings, "Use Google's home screen" and "Try again", rather than crashing (a crash with Google's launcher disabled would just relaunch it).

## Build and install

```
./deploy.sh            # build.sh + adb install + AOT compile
adb shell cmd package set-home-activity com.beacon2.home    # make it the default (package name; component ignored on Android 10)
```

Requirements: Homebrew `openjdk@17`, `android-commandlinetools` with `platforms;android-29` and `build-tools;34.0.0`. The signing key is `~/.a9g-home/release.jks` (password in `~/.a9g-home/storepass`); back both up, or future updates will not install over the current version.

Never rename `.HomeActivity`: Android drops the HOME preference when the HOME component set changes.

## Photos

`assets/photos/` holds 20 landscape photos from Wikimedia Commons, cropped to 1920×1080. Licences and sources are in `assets/photos/photos.json`; CC BY / CC BY-SA photos are credited on screen. Font: Schibsted Grotesk, SIL OFL 1.1 (`assets/fonts/OFL.txt`).

## Recovery card

- **Home shows Google's home screen again.** Something re-enabled it (an update or reinstall of "Android TV Home" does this automatically). This app is still in the apps row. To make it Home again: Settings → Device Preferences → Developer options → USB debugging on; then `adb connect 192.168.86.171:5555 && adb shell pm disable-user --user 0 com.google.android.tvlauncher`; then debugging off. Never tap Update or Enable on "Android TV Home" in the Play Store.
- **You want Google's home back.** All apps → Restore stock home → Continue → Enable → press Home. Or Settings → Apps → See all apps → Android TV Home → Enable.
- **The home screen shows "hit a problem".** It crashed within the last minute; choose Try again, or Use Google's home screen.
- **Home is a blank screen.** Only if this app was removed while Google's launcher was off. Say "Hey Google, open Settings", then Apps → See all apps → Android TV Home → Enable.
- **Notifications.** All apps → BRAVIA notifications.
- **Never uninstall this app before turning Android TV Home back on.**

