# Signing key

`release.jks` (PKCS12, alias `a9g-home`) signs every build; `storepass` is its password (store and key).
Android only installs an update over the current app when it is signed with this same key.

Committed here deliberately as the backup, at Kevin's request: the repo is private and behind GitHub MFA,
and a build signed with it can only reach the TV over ADB on the home network.

To build on a new machine: `mkdir -p ~/.a9g-home && cp signing/release.jks signing/storepass ~/.a9g-home/ && chmod 600 ~/.a9g-home/*`
