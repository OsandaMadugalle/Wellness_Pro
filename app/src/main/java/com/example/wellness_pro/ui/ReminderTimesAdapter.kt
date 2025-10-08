package com.example.wellness_pro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReminderTimesAdapter(
    private val reminderTimes: MutableList<String>,
    private val onRemoveClicked: (position: Int) -> Unit
) : RecyclerView.Adapter<ReminderTimesAdapter.ViewHolder>() {

    // Use device default locale so parsing/formatting matches the rest of the app
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    init {
        sortTimes()
    }

    private fun sortTimes() {
        reminderTimes.sortWith(compareBy { LocalTime.parse(it, timeFormatter) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder_time, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val time = reminderTimes[position]
        holder.textViewReminderTime.text = time
        holder.buttonRemoveTime.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onRemoveClicked(adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = reminderTimes.size

    fun addTime(time: String) {
        if (!reminderTimes.contains(time)) { // Prevent duplicates
            reminderTimes.add(time)
            sortTimes()
            // Using notifyDataSetChanged() is the simplest way to update the UI after a sort.
            notifyDataSetChanged()
        }
    }

    fun removeTime(position: Int) {
        if (position >= 0 && position < reminderTimes.size) {
            reminderTimes.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, reminderTimes.size)
        }
    }

    fun getTimes(): List<String> {
        return reminderTimes.toList() // Return a copy
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewReminderTime: TextView = itemView.findViewById(R.id.textViewReminderTimeItem)
        val buttonRemoveTime: ImageButton = itemView.findViewById(R.id.buttonRemoveTimeItem)
    }
}
