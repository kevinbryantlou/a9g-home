# A9G Home

A home screen app for one TV: Kevin's Sony XBR-55A9G (Android TV 10). Plain Java, no libraries, built without Gradle.

- **Home**: the service names are the menu (YouTube, Prime Video, Netflix, PS4 on HDMI 2, Spotify), over a photo slideshow behind a single neutral shade. Inputs opens Sony's input picker; All apps lists every TV app.
- **Ways back to Google's home screen**: All apps → Restore stock home; long-press Menu; uninstall; or `adb shell cmd package set-home-activity com.google.android.tvlauncher`. A crash also hands HOME back automatically.

## Build and install

```
./deploy.sh            # build.sh + adb install + AOT compile
adb shell cmd package set-home-activity com.beacon2.home    # make it the default (package name; component ignored on Android 10)
```

Requirements: Homebrew `openjdk@17`, `android-commandlinetools` with `platforms;android-29` and `build-tools;34.0.0`. The signing key is `~/.a9g-home/release.jks` (password in `~/.a9g-home/storepass`); back both up, or future updates will not install over the current version.

Never rename `.HomeActivity`: Android drops the HOME preference when the HOME component set changes.

## Photos

`assets/photos/` holds 20 landscape photos from Wikimedia Commons, cropped to 1920×1080. Licences and sources are in `assets/photos/photos.json`; CC BY / CC BY-SA photos are credited on screen. Font: Schibsted Grotesk, SIL OFL 1.1 (`assets/fonts/OFL.txt`).
