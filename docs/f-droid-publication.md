# Publishing on F-Droid

Notes on what's needed to get pills-in-time (MedTracker) accepted into the F-Droid catalog, based on the state of the repo as of 2026-07-20.

## Already satisfied

- **License** — MIT (`LICENSE`), on F-Droid's list of approved licenses.
- **Public source repository** — hosted on GitHub (`soksanichenko/pills-in-time`).
- **No tracking/analytics** — no such dependencies in the project.
- **Plain Gradle build** — `./gradlew bundleRelease` / `assembleRelease` works without any mandatory proprietary steps. The Gradle Play Publisher plugin (`com.github.triplet.play`) and its publish task are effectively inert unless `release-manager-key.json` is present, so it doesn't get in the way of an F-Droid build.

## Main blocker: Google Play Services dependency

Google Drive backup (`DriveAuthManager`) depends on:

- `androidx.credentials:credentials-play-services-auth`
- `com.google.android.libraries.identity.googleid:googleid`
- `com.google.android.gms:play-services-auth`

(declared in `app/build.gradle.kts:144-146`)

These are proprietary Google libraries requiring Google Play Services on-device. F-Droid's build server can still compile them (they're ordinary artifacts on the `google()` Maven repo), but:

- the app listing gets tagged with anti-features (`NonFreeDep` / `NonFreeNet`);
- Drive backup won't work at all on de-googled devices, which make up a large part of the F-Droid user base.

**Recommended fix:** add a separate build flavor (e.g. `fdroid`) that excludes these dependencies and only exposes local file backup (`BackupRepository.backupToFile` / `restoreFromFile`, which uses Android's Storage Access Framework and has no Google dependency). Alternatively, ship as-is and accept the anti-feature flags — F-Droid doesn't reject the app for this, but it does make the listing less attractive.

## Organizational gaps to close

1. **Git release tags** — none exist yet (`git tag -l` is empty). F-Droid builds a specific `versionCode`/`versionName` off a specific tag or commit, so releases need to be tagged (e.g. `v1.0` for the current `versionCode 14`).

2. **Fastlane metadata** — F-Droid pulls the store listing (description, screenshots, icon, changelog) from `fastlane/metadata/android/<locale>/`:
   - `short_description.txt`, `full_description.txt`
   - `changelogs/<versionCode>.txt`
   - `images/` (icon, feature graphic, phone screenshots)

   This structure doesn't exist in the repo yet.

3. **Build metadata (recipe)** — a YAML file describing the build for `fdroiddata`, with fields like `Categories`, `License`, `AutoName`, `Repo`, and a `Builds:` section (commit, versionCode/versionName, subdir, gradle target — plus `gradle: fdroid` if the flavor split above is done).

4. **Submission** — via a "Request For Packaging" issue, or a merge request adding the recipe YAML directly, against the `fdroiddata` repo (hosted on Codeberg/GitLab under the F-Droid org).

5. **Signing** — no need to hand over the release keystore. F-Droid signs the APK it builds with its own key by default (unless opting into the reproducible-builds process where the developer's signature is published alongside).
