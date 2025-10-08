# Wellness Pro

Wellness Pro is an Android app focused on simple wellness tracking: mood journaling, habit tracking, and hydration reminders. This repository contains the Android application code (Kotlin), layouts, resources, and supporting modules.

This README gives an overview, quick start instructions, architecture highlights, and links to more detailed documentation in the `docs/` folder.

## Key Features
- Mood Journal: log moods, attach notes, view history and trends (MPAndroidChart used for charts).
- Habits: create and track habits over time with XP-based progress and a widget for today’s habits.
- Hydration: track daily glasses and configure reminders and goals.
- Notifications & Reminders: uses WorkManager / AlarmManager fallbacks for reminders and a receiver for boot handling.

## Repository layout (important files)
- `app/` - Android app module
  - `src/main/java/com/example/wellness_pro/` - Kotlin source code (activities, viewmodels, remiders, widgets)
  - `src/main/res/layout/` - XML layouts for activities and reusable components
  - `src/main/res/values/` - strings, colors, dimens, themes
  - `AndroidManifest.xml` - app manifest and exported components
- `build.gradle.kts` & `settings.gradle.kts` - Gradle configuration

For a complete file-level inventory and architecture overview, see `docs/architecture.md`.

## Quick start (development)
1. Prerequisites
   - Android Studio (recommend latest stable), SDK platforms including API 26–36
   - JDK 11
2. Open the project in Android Studio using the repository root.
3. Let Gradle sync and resolve dependencies (project uses Compose, Room, MPAndroidChart, Glide, WorkManager).
4. Build and run on emulator or device (minSdk 26):

```pwsh
./gradlew assembleDebug
```

5. To run in Android Studio, click Run (Shift+F10) after selecting a run configuration.

## Where to look
- UI / screens: `app/src/main/java/com/example/wellness_pro` and `app/src/main/res/layout`
- Room DB and ViewModel: `app/src/main/java/com/example/wellness_pro/db` and `.../viewmodel`
- Reminders: `app/src/main/java/com/example/wellness_pro/reminders`
- Widget: `app/src/main/java/com/example/wellness_pro/widgets`

## Contributing
See `CONTRIBUTING.md` for guidelines on contributions, code style, and PR workflow.

## License
This project includes an MIT license by default. See `LICENSE`.

---
For more detailed developer docs, read the files under `docs/` (architecture, setup, development, FAQ).
