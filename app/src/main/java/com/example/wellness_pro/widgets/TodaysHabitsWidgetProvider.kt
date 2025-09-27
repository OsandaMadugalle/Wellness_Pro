package com.example.wellness_pro.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.RemoteViews
import com.example.wellness_pro.Habit // Import your Habit model
import com.example.wellness_pro.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TodaysHabitsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "HabitsWidgetProvider"
        private const val HABITS_PREFS_NAME = "PlayPalHabits"
        private const val HABITS_LIST_KEY = "habits_list_json"

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_todays_habits)
            var todaysTotalHabits = 0
            var todaysCompletedHabits = 0

            try {
                val habits = loadHabits(context)
                val todayCalendar = Calendar.getInstance()
                val todayNormalizedTimestamp = normalizeTimestampToDay(System.currentTimeMillis())

                for (habit in habits.filter { !it.isArchived }) {
                    if (isHabitScheduledForToday(habit, todayCalendar)) {
                        todaysTotalHabits++
                        if (isHabitCompletedOn(habit, todayNormalizedTimestamp)) {
                            todaysCompletedHabits++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading or processing habits for widget", e)
                // Keep counts at 0, default view will be shown
            }

            val habitCompletionPercentage = if (todaysTotalHabits > 0) {
                (todaysCompletedHabits * 100) / todaysTotalHabits
            } else {
                0
            }
            val percentageText = "$habitCompletionPercentage%"

            views.setTextViewText(R.id.widget_title, "Today's Habits")
            views.setTextViewText(R.id.widget_percentage, percentageText)
            views.setProgressBar(R.id.widget_progress_bar, 100, habitCompletionPercentage, false)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun loadHabits(context: Context): List<Habit> {
            val prefs: SharedPreferences = context.getSharedPreferences(HABITS_PREFS_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString(HABITS_LIST_KEY, null)
            val typeToken = object : TypeToken<MutableList<Habit>>() {}.type
            return if (json != null) {
                gson.fromJson<MutableList<Habit>>(json, typeToken) ?: emptyList()
            } else {
                emptyList()
            }
        }

        private fun normalizeTimestampToDay(timestamp: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        private fun isHabitCompletedOn(habit: Habit, dayTimestamp: Long): Boolean {
            return habit.completionHistory[dayTimestamp] == true
        }

        private fun isHabitScheduledForToday(habit: Habit, todayCalendar: Calendar): Boolean {
            if (habit.schedule.equals("Daily", ignoreCase = true)) {
                return true
            }
            // For weekly schedules like "Mon,Wed,Fri"
            // Calendar.DAY_OF_WEEK returns 1 for Sunday, 2 for Monday, ..., 7 for Saturday
            val currentDayOfWeek = todayCalendar.get(Calendar.DAY_OF_WEEK)
            val dayAbbreviation = SimpleDateFormat("EEE", Locale.getDefault()).format(todayCalendar.time)

            // Example: schedule might be "Mon,Wed,Fri" or "Mon, Wed, Fri"
            val scheduledDays = habit.schedule.split(',').map { it.trim().lowercase(Locale.getDefault()) }

            return scheduledDays.any { dayString ->
                 dayString.equals(dayAbbreviation.lowercase(Locale.getDefault()), ignoreCase = true) ||
                 dayString.equals(getDayFullName(currentDayOfWeek).lowercase(Locale.getDefault()), ignoreCase = true) // Allow full names too
            }
        }

        // Helper for isHabitScheduledForToday to handle full day names if present in schedule string
        private fun getDayFullName(calendarDayOfWeek: Int): String {
            return when (calendarDayOfWeek) {
                Calendar.SUNDAY -> "Sunday"
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                else -> ""
            }
        }
    }
}
