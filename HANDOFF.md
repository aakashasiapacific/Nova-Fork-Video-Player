# Handoff: read this first

Nova Fork is an Android video player in the spirit of VLC, with a TMDB poster and backdrop library and
separate phone and tablet UIs. This file is for a new Claude session picking the project up.

## Where things are

- **Repo:** `aakashasiapacific/Nova-Fork-Video-Player`
- **Branches and PRs:**
  - `ccr-987fc293-7gnds9` is the newest work: the audio engine, the player volume change and these
    docs. Its PR is #2 (draft), which merges into `ccr-d205f787-t31r46`:
    https://github.com/aakashasiapacific/Nova-Fork-Video-Player/pull/2
  - `ccr-d205f787-t31r46` holds everything before that. Its PR is #1 (draft), into the default branch:
    https://github.com/aakashasiapacific/Nova-Fork-Video-Player/pull/1
  - A new session builds on `ccr-987fc293-7gnds9` (or on its own branch started from it) and watches
    PR #2. The previous session stopped watching both PRs.
- **Latest APK** (always the newest CI build, arm64 for the owner's Galaxy S21 FE):
  https://github.com/aakashasiapacific/Nova-Fork-Video-Player/raw/apk/NovaFork-latest-arm64-v8a.apk
- **Docs:** `docs/ARCHITECTURE.md` (modules, contracts, toolchain: read it before coding) and
  `docs/APPLE-TV-SPEC.md` (target UI spec), and `docs/REDESIGN-IDEAS.md` (the owner's decisions,
  which come before the spec).
- **Last green build:** CI run 28 at `afa0c35` (branch `ccr-987fc293-7gnds9`, PR #2). 308 app tests and 394 core tests pass.

## Building and testing

- **The app can't be built in the cloud container.** The Android SDK and Google Maven are blocked.
  GitHub Actions (`.github/workflows/build.yml`) is the only real build.
  - The **build** job runs `:core:test assembleRelease` and publishes the APKs to the `apk` branch
    (fixed `NovaFork-latest-<abi>.apk` names). On failure it pushes the logs to the `ci-logs` branch.
  - The **screens** job runs `:app:testDebugUnitTest` (Robolectric and Roborazzi screenshots). It publishes
    PNGs, test XML and the generated Room schema JSON to the `screens` branch.
  - Put `[skip ci]` in a commit message to save a WIP commit without building.
- **The `core` module (pure Kotlin/JVM) can be tested locally.** Make a small Gradle project anywhere
  (outside the repo), with this `settings.gradle.kts`:
  ```kotlin
  pluginManagement { repositories { maven("https://maven-central.storage-download.googleapis.com/maven2/"); gradlePluginPortal() } }
  dependencyResolutionManagement { repositories { maven("https://maven-central.storage-download.googleapis.com/maven2/") } }
  rootProject.name = "core-harness"
  include(":core")
  project(":core").projectDir = file("/home/user/nova-fork-video-player/core")
  ```
  and this `build.gradle.kts`:
  ```kotlin
  plugins {
      id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
      id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
  }
  ```
  Then run `/opt/gradle/bin/gradle :core:test --no-daemon -q`. Use Google's mirror of Maven Central,
  as above: Maven Central itself answers the container with "429 Too Many Requests".
- **Regexes:** escape every literal `[ ] { }`. Android uses ICU, which is stricter than the JVM (see
  `tools/regex-check/`, which CI runs).
- **App code must be compile-exact.** Use explicit imports and real signatures, and grep the repo for
  existing patterns. libVLC is pinned at 3.7.2, because 3.7.3+ needs Kotlin 2.2.
- **Room.** The schema is exported to `app/schemas/`; the database is now at version 4, with manual
  migrations in `NovaDatabase.MIGRATIONS`. After a version bump, copy the new `N.json` from the `screens`
  branch into `app/schemas/`.
- **Keys** (in `gradle.properties`, each overridable by an env var / repository secret of the same name):
  - `TMDB_API_KEY` is set: a built-in v3 key from the owner.
  - `OPENSUBTITLES_API_KEY` is empty. The owner hasn't sent one yet; users can type a key in Settings.

## What's done

- **Player:** libVLC playback, tracks, subtitles, gestures, PiP, resume and up-next.
- **Library:** TMDB posters with local artwork as a fallback, and Identify.
- **Phone and tablet UIs:** the current Material design.
- **Playlists and queue:** with shuffle and repeat (Room v2).
- **Background audio:** `PlaybackService` with a MediaSession notification.
- **Chromecast:** through libVLC renderers.
- **Network sources (Room v3):** SMB, FTP, SFTP, NFS, DLNA/UPnP, WebDAV, Jellyfin, Emby and Plex.
- **Audio engine (Room v4), local only:** music from the phone and picked folders, tags, cover art
  (cover.jpg or embedded), synced .lrc lyrics, songs in playlists, the watchlist table, a music
  player with its own notification, and a plain temporary Audio tab to test it. See
  `docs/ARCHITECTURE.md` › Music. Built on branch `ccr-987fc293-7gnds9` (PR #2, into this branch).
- **OpenSubtitles downloads.**
- **Backup and restore:** a JSON file through the file picker.
- **Crash reporter:** an in-app Share dialog.
- **Library diagnostics:** Settings › About.
- **Player:** no brightness/volume swipes (the phone's own apply); the volume keys show an
  iOS-style pill instead of the system panel (`docs/REDESIGN-IDEAS.md` §7).

Each feature round had independent reviews and a fix pass.

**Fixed and confirmed by the owner on build 24: no videos appeared on the phone.** The file-name
parser's `BRACKETS` regex in `core/parse/ReleaseTokens.kt` had an unescaped `}`. The JVM accepts that,
so every test passed, but Android's regex engine (ICU) rejects it. As a result, every scan on a phone
failed after 0.1 s, from the first version until this fix. It was found through the owner's Library
diagnostics report (Settings › About), which is the first thing to ask for when the library looks wrong
on a device. `tools/regex-check/run.sh` now compiles every regex with ICU in CI, so this can't happen
again unnoticed. The owner's phone has All files access but not READ_MEDIA_VIDEO; since build 23, All
files access counts as full access.

**Confirmed by the owner on build 28 (screenshot of Home):**
- Live TMDB lookups work on the phone: posters, backdrops and ratings.
- The library scan works.
- Watched marks show.
- The Audio tab is in the bottom bar.

The owner hasn't yet reported on music playback, lyrics, the lock-screen controls or the new
volume indicator.

## Open items, most urgent first

1. **Apple TV-style UI redesign.** Read `docs/REDESIGN-IDEAS.md` first: it holds the owner's decisions, which come
   before the spec. These include a new nav (Video / Audio / Settings / Search), no app title, a clear status bar, a big hero,
   and a new Apple Music-style Audio tab. All the open questions are now settled (section 6 of that file). Don't build until the owner says to.
   - The owner wants a "complete ditto" of the Apple TV app (Android/iOS). Their brother will send iPhone
     screenshots and recordings. Update `docs/APPLE-TV-SPEC.md` from them.
   - Earlier redesign work, paused before the backend rounds, is in commit `f41c97d` (later commits went
     back to the Material UI). Reuse it where it still fits the current contracts.
   - Keep the phone and tablet component families separate.
2. **OpenSubtitles API key:** put it in `gradle.properties` (`OPENSUBTITLES_API_KEY=`) once the owner
   sends it.
3. **Not yet tested on a real device:**
   - background audio and lock-screen controls;
   - Chromecast;
   - network and media servers;
   - subtitle downloads;
   - the Audio tab (music scan, album art, lyrics), music in the background, and the music
     notification;
   - the player's iOS-style volume indicator and its volume-key handling.
4. **Music not yet included in:** backup/restore (song-only playlists are left out of backups for
   now), Library diagnostics, or search.
5. **Ideas noted, not built:** see `docs/REDESIGN-IDEAS.md` for the full list. Sections 0–6 cover the
   redesign; section 7 (the player's brightness and volume) is done.

## Working with the owner

- **Usage limits.** The owner is on limited usage and doesn't want sessions burned:
  - Work only for the time they give, then stop: commit (`[skip ci]` if it's WIP), push, report, and wait.
  - Prefer working solo. For a big round, one independent review agent before or alongside CI
    is fine; the owner agreed to that for the audio engine. Multi-agent workflows used about 1–2.4M tokens per feature round, so run one
    only when the owner agrees.
- **Communication:**
  - Ask clarifying questions before long answers (the owner's stated preference).
  - Write in plain language and give the APK link after each green build.
  - Don't claim a fix works on their phone until the owner confirms it. Say what is proven (tests, CI)
    and what isn't.
- After pushing, CI results arrive as events on the PR the session watches (PR #2 now). Check `apk` / `ci-logs` / `screens` to see the outcome.
