## Architecture Overview — Wellness Pro

This document outlines the high-level architecture and code organization for the Wellness Pro Android app. It is intended for contributors who want to understand app structure and data flow.

### High level
- Platform: Native Android app written in Kotlin.
- App module (Gradle): `app/`.
- Key libraries: AndroidX (lifecycle, room, recyclerview), Material Components, MPAndroidChart, Glide, WorkManager, Kotlin Coroutines.

### Packages and responsibilities
- `com.example.wellness_pro` — App entrypoints and screens (activities): Dashboard, Profile, Habits, Hydration, Settings, Launch.
- `com.example.wellness_pro.ui` — UI-related activities that were grouped under `ui`, such as `MoodLogActivity`, `MoodHistoryActivity`, `HydrationActivity`, `SetHydrationActivity`, `SettingsActivity`.
- `com.example.wellness_pro.viewmodel` — ViewModel classes used to expose data for UI, including `MoodViewModel` and factory classes.
- `com.example.wellness_pro.db` — Room database schema and DAO(s) for persistently storing mood entries and other structured data.
- `com.example.wellness_pro.reminders` — BroadcastReceiver and Worker classes for scheduling and delivering hydration reminders.
- `com.example.wellness_pro.widgets` — App widget provider (today’s habits widget).
- `com.example.wellness_pro.navbar` — Base activities that implement the bottom navigation behavior.

### Data flows
- Mood flow
  - User logs moods in `MoodLogActivity` which inserts into Room via a DAO.
  - `MoodHistoryActivity` observes the DAO through `MoodViewModel` (Kotlin Flows) and displays a RecyclerView and a LineChart (MPAndroidChart). The mood history screen filters by time windows (1,7,14,30 days or all) and updates both the list and chart using a combined flow of entries + selected window.

- Habits flow
  - Habits are persisted in SharedPreferences as JSON (helper methods in `SetHabitScreen` / `HabitsScreen`). The profile screen reads habits count to show a summary tile.
  - A homescreen widget displays today’s habits using `TodaysHabitsWidgetProvider`.

- Hydration flow
  - Hydration state (daily goal and per-day intake) is persisted in SharedPreferences. Reminders are scheduled using `HydrationReminderManager` with a `HydrationAlarmReceiver` and a `HydrationReminderWorker` fallback.

### UI patterns
- Header pattern: several screens use a three-slot header row (left placeholder/action, centered autosizing title, right action). An inset-preservation pattern preserves original header padding and adds status bar insets at runtime.
- Reusable components: reusable nav bar included via `layout_reusable_nav_bar.xml`.

### Notable implementation details
- Coroutines & Flows: used in ViewModels and activities for reactive updates.
- Room: used for mood entries to allow efficient queries and offline persistence.
- MPAndroidChart: used for mood trend visualization (LineChart) and updated using the filtered mood entries.
- Glide: image loading for avatars.

### Where to start reading source code
1. `app/src/main/java/com/example/wellness_pro/LaunchScreen.kt` - startup flow.
2. `app/src/main/java/com/example/wellness_pro/ui/MoodHistoryActivity.kt` - combined Flow example, chart integration.
3. `app/src/main/java/com/example/wellness_pro/db` - database schema and DAO usage.
4. `app/src/main/res/layout` - examine activity layouts and the header pattern.

If you need component-level diagrams or sequence diagrams for flows, ask and I can generate them.
