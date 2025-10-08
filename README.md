# Wellness_Pro
# Wellness Pro

Wellness Pro is an Android app for simple wellness tracking: mood journaling, habit tracking, and hydration reminders. The app is written in Kotlin and uses AndroidX and Material components.

This README is a concise developer reference: how to build, where key code lives, and repository hygiene notes.

## Quick facts
- Language: Kotlin
- Build: Gradle (Kotlin DSL)
- Module: `app/` (Android application)
- Minimum SDK: 26
- SDK/compile target: 36

## Build & run (developer)
1. Install Android Studio and the Android SDK that matches the project.
2. From the project root (where `gradlew` lives):

```pwsh
cd E:/AndroidStudioProjects/Wellness_Pro
.\gradlew.bat assembleDebug -x lint
```

3. Import the project into Android Studio and run or debug from there.

Notes:
- If Gradle prompts for SDK/platform updates, install the missing components via the SDK Manager.
- The project builds successfully on this machine after removing Firebase artifacts.

## Project layout (important locations)
- `app/` - Android app module
  - `app/src/main/java/com/example/wellness_pro/` - Kotlin source code
  - `app/src/main/res/layout/` - XML layouts
  - `app/src/main/res/values/` - strings, colors, dimens, themes
  - `app/src/main/AndroidManifest.xml` - manifest
- Root-level Gradle files: `build.gradle.kts`, `settings.gradle.kts`
- `gradle/` and `gradle/wrapper` contain version pinning and wrapper config.

## Features overview
- Mood Journal: log moods, view history and trends.
- Habits: create and track habits with progress UI.
- Hydration: track water intake and configure reminders locally.
- Reminders: uses WorkManager / AlarmManager fallback for scheduling reminders.

## Repository hygiene
- Firebase: This project does NOT use Firebase. Firebase configuration files and Gradle plugin references have been removed from the project and are not tracked. If you see references to Firebase in working files, they are residual comments and safe to ignore.
- IDE files: The `.idea/` directory contains user-specific settings and should generally be excluded from git. Consider removing it from the repo and adding `.idea/` to `.gitignore`.
- Build outputs: `app/build/` and other `build/` directories are generated and should be ignored.

## Common tasks
- Run tests (unit/instrumented) via Android Studio or Gradle tasks.
- Lint and formatting: follow the project's code style settings in `.editorconfig` and any linters configured in Gradle.

## Contributing
- Please open issues or PRs on the main repository. Follow code style and prefer small, reviewable changes.

## Contact / Next steps I can do for you
- Remove `.idea/` from git and add it to `.gitignore` (I can run the git commands if you want).
- Run a CI workflow to ensure builds across multiple SDK versions.

---

If you want any additional docs (architecture.md, development setup for new contributors, or API reference), tell me which area you want first and I'll add a `docs/` file.
