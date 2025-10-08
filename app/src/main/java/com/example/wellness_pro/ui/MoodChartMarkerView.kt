package com.example.wellness_pro.ui

import android.content.Context
import android.widget.TextView
import com.example.wellness_pro.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple MarkerView that shows the date and mood level for a tapped entry.
 */
class MoodChartMarkerView(context: Context) : MarkerView(context, R.layout.marker_mood_chart) {

    private val tvMarkerText: TextView = findViewById(R.id.tvMarkerText)
    private val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val ts = e.x.toLong()
            val mood = e.y.toInt()
            val date = try {
                dateFormat.format(Date(ts))
            } catch (ex: Exception) {
                e.x.toLong().toString()
            }
            tvMarkerText.text = "$date\n${moodLabel(mood)} ($mood)"
        } else {
            tvMarkerText.text = ""
        }
        super.refreshContent(e, highlight)
    }

    private fun moodLabel(level: Int): String {
        return when (level) {
            1 -> context.getString(R.string.mood_very_bad)
            2 -> context.getString(R.string.mood_bad)
            3 -> context.getString(R.string.mood_neutral)
            4 -> context.getString(R.string.mood_good)
            5 -> context.getString(R.string.mood_very_good)
            else -> context.getString(R.string.unknown)
        }
    }
}
