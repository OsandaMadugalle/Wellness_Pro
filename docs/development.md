## Development Guide — Wellness Pro

This document helps contributors get productive: code layout, conventions, and where to extend the app.

### Coding conventions
- Language: Kotlin (targeting JVM 11 for Android)
- Layouts: XML for screens; small Compose usage is enabled but optional.
- View binding pattern: Activities use `findViewById` and Kotlin synthetic patterns; prefer explicit `findViewById` or migrate to ViewBinding gradually.
- Coroutines: Use lifecycleScope for UI-bound coroutines and ViewModelScope inside ViewModels.

### Adding a new screen
1. Create a layout under `res/layout` named `activity_<screen_name>.xml`.
2. Add an Activity or Fragment in `app/src/main/java/com/example/wellness_pro` (or `.../ui` package) that inflates the layout.
3. Register the Activity in `AndroidManifest.xml` if needed.
4. Add navigation entry points using the existing bottom nav or create a new intent flow.

### Database & Room
- Room is used for mood entries. Add entities and DAOs in `app/src/main/java/com/example/wellness_pro/db` then update the AppDatabase.
- Re-run KSP annotation processing via a clean build to generate DAO implementations.

### ViewModel & Flows
- Use `MutableStateFlow` in activities for UI state and `combine` to merge data flows from ViewModel with local UI state like selection windows.

### Tests
- Unit tests: `app/src/test/java` contains example unit tests using JUnit. Run with:

```pwsh
./gradlew test
```

- Instrumentation tests: use Android Studio to run instrumentation tests on emulator/device.

### Code review checklist
- Build succeeds locally.
- No unused imports or resources.
- Strings added to `res/values/strings.xml` for user-facing text.
- Layouts are responsive (use autosize text or constraints where appropriate).
