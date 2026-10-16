# Handoff: read this first

Nova Fork is an Android video player in the spirit of VLC, with a TMDB poster and backdrop library and
separate phone and tablet UIs. This file is for a new Claude session picking the project up.

## Where things are

- **Repo:** `aakashasiapacific/Nova-Fork-Video-Player`
- **Branch:** `ccr-d205f787-t31r46`
- **PR:** #1 (draft): https://github.com/aakashasiapacific/Nova-Fork-Video-Player/pull/1
- **Latest APK** (always the newest CI build, arm64 for the owner's Galaxy S21 FE):
  https://github.com/aakashasiapacific/Nova-Fork-Video-Player/raw/apk/NovaFork-latest-arm64-v8a.apk
- **Docs:** `docs/ARCHITECTURE.md` (modules, contracts, toolchain: read it before coding) and
  `docs/APPLE-TV-SPEC.md` (target UI spec).
- **Last green build:** CI run 23 at `098898d`. 291 app tests and 371 core tests pass.

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
  pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
  dependencyResolutionManagement { repositories { mavenCentral() } }
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
  Then run `/opt/gradle/bin/gradle :core:test --no-daemon -q`.
- **Regexes:** escape every literal `[ ] { }`. Android uses ICU, which is stricter than the JVM (see
  `tools/regex-check/`, which CI runs).
- **App code must be compile-exact.** Use explicit imports and real signatures, and grep the repo for
  existing patterns. libVLC is pinned at 3.7.2, because 3.7.3+ needs Kotlin 2.2.
- **Room.** The schema is exported to `app/schemas/`; the database is now at version 3, with manual
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
- **OpenSubtitles downloads.**
- **Backup and restore:** a JSON file through the file picker.
- **Crash reporter:** an in-app Share dialog.
- **Library diagnostics:** Settings › About.

Each feature round had independent reviews and a fix pass.

## Open items, most urgent first

1. **Bug: videos without posters never appear. Root cause found and fixed; waiting for the owner's
   confirmation.**
   - **What the owner's Library diagnostics report showed** (Android 16, API 36):
     - Every scan failed after 0.1 s with `ExceptionInInitializerError` in
       `core.parse.ReleaseTokens`.
     - Its `BRACKETS` regex had an unescaped `}`. The JVM's regex engine accepts that; Android's engine
       (ICU) rejects it with `U_REGEX_RULE_SYNTAX`.
     - So on phones the file-name parser never loaded and no scan ever finished, since the first version.
   - **The fix:** escape every literal bracket and brace.
   - **Proof:** all 64 patterns in the app now compile with ICU 74.
   - **Guard against a repeat:** `tools/regex-check/run.sh` compiles every static Pattern/Regex with ICU,
     and CI now fails if Android would reject one.
   - **Report details:**
     - READ_MEDIA_VIDEO is *denied* and All files access is *allowed* (the owner was looking at the wrong
       setting).
     - Since build 23 the app counts All files access as full access.
     - MediaStore lists only 2 videos on the phone: one in WhatsApp Video and one in
       `snaptube/download/SnapTube Video`.
   - **If anything is still wrong,** ask for a new Library diagnostics report (Settings › About).
2. **Apple TV-style UI redesign.**
   - The owner wants a "complete ditto" of the Apple TV app (Android/iOS). Their brother will send iPhone
     screenshots and recordings. Update `docs/APPLE-TV-SPEC.md` from them.
   - Earlier redesign work, paused before the backend rounds, is in commit `f41c97d` (later commits went
     back to the Material UI). Reuse it where it still fits the current contracts.
   - Keep the phone and tablet component families separate.
3. **OpenSubtitles API key:** put it in `gradle.properties` (`OPENSUBTITLES_API_KEY=`) once the owner
   sends it.
4. **Not yet tested on a real device:**
   - background audio and lock-screen controls;
   - Chromecast;
   - network and media servers;
   - subtitle downloads;
   - live TMDB lookups (TMDB is blocked from the container).

## Working with the owner

- **Usage limits.** The owner is on limited usage and doesn't want sessions burned:
  - Work only for the time they give, then stop: commit (`[skip ci]` if it's WIP), push, report, and wait.
  - Prefer working solo. Multi-agent workflows used about 1–2.4M tokens per feature round, so run one
    only when the owner agrees.
- **Communication:**
  - Ask clarifying questions before long answers (the owner's stated preference).
  - Write in plain language and give the APK link after each green build.
  - Don't claim a fix works on their phone until the owner confirms it. Say what is proven (tests, CI)
    and what isn't.
- After pushing, CI results arrive as PR #1 events. Check `apk` / `ci-logs` / `screens` to see the outcome.
