## FAQ & Troubleshooting

This page lists common problems and how to resolve them.

Q: The app crashes on launch with a NullPointerException in an Activity.
- A: Check that the layout referenced by `setContentView` contains all view IDs that the Activity expects. Recent refactors may have renamed or removed IDs. Either restore the expected ID in the XML or update the Activity to use the new ID.

Q: Changes to Room entities don't seem to apply.
- A: Clean and rebuild to let KSP / Room generate DAO implementations: `./gradlew clean assembleDebug`.

Q: My layout title is no longer centered when I remove the left or right button.
- A: The app uses a 3-slot header pattern (left placeholder, center title, right placeholder). Ensure the middle title uses autosize and the placeholders maintain width (or use invisible views) so the title stays centered.

Q: The Mood History chart doesn't update when changing chips.
- A: Watch Logcat for the debug logs in `MoodHistoryActivity` (updated by recent edits). Verify you see messages for chip selection and chart data combine; if you don't, check that the `ChipGroup` has `app:singleSelection="true"` and that no overlaying view blocks touches.

Q: App widget not updating.
- A: Confirm the widget provider is registered in `AndroidManifest.xml` and the correct `AppWidgetProviderInfo` exists under `res/xml/`.
