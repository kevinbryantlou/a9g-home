# Signing key

The release key is **not** in this repo. `build.sh` signs with `~/.a9g-home/release.jks`
(PKCS12, alias `a9g-home`) and reads its password from `~/.a9g-home/storepass`.

Android only installs an update over the current app when it is signed with the same key, so
keep an off-GitHub backup of both files (password manager or encrypted drive). If the key is
lost, the fix is to uninstall and reinstall the app with a new key; see CLAUDE.md for the safe
order (re-enable the stock home first).

To build on a new machine: `mkdir -p ~/.a9g-home && cp <backup>/release.jks <backup>/storepass ~/.a9g-home/ && chmod 600 ~/.a9g-home/*`

History: the original key was committed here on 2026-10-22 while the repo was public. It was
rotated on 2026-11-12; the old key can no longer update the installed app.
