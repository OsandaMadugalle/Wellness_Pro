## Setup and Run — Wellness Pro

This page provides step-by-step instructions for setting up the development environment, building, and running the app.

### Requirements
- JDK 11
- Android Studio (Electric Eel or later recommended)
- Android SDK platforms for API level 26 — 36 (project uses compileSdk 36)
- Gradle wrapper included in the repo (use `./gradlew`)

### Clone & Open
1. Clone the repository.
2. Open Android Studio and choose `Open` → point to the repository root.
3. Let Gradle sync and download dependencies. This project uses KSP, Room, Coroutines, MPAndroidChart, Glide and Material Components. It also includes Compose support (Compose BOM), but screens are primarily XML-based.

### Build from command line
From the project root, run:

```pwsh
./gradlew assembleDebug
```

To install the debug APK to a connected device/emulator:

```pwsh
./gradlew installDebug
```

### Run from Android Studio
- Use the default Run configuration after selecting a device or emulator.
- If you see resource errors after layout edits, try `Build` → `Clean Project` then `Build` → `Rebuild Project`.

### Emulator / Device recommendations
- Use an emulator image with Google Play if you want to test notifications or other Google Play related features.
- Minimum API: 26 (Android 8.0). Recommended: 33 or 34 for feature parity.

### Common build issues & fixes
- Missing string or resource: If you edit layouts and add IDs referenced by code, add the corresponding IDs or update Kotlin code to avoid runtime crashes.
- KSP / annotation processing: If KSP-generated classes (Room) are missing, run a clean build to generate artifacts: `./gradlew clean assembleDebug`.
- Gradle JVM compatibility: Use JDK 11 (set in `gradle.properties` or Android Studio settings) to match `javaVersion` in Gradle.

### Logging & debugging
- Use Android Studio Logcat to view runtime logs. The project logs some debug messages for Flow combines (e.g., in `MoodHistoryActivity`), which can help trace the UI flows when interacting with chips and charts.
