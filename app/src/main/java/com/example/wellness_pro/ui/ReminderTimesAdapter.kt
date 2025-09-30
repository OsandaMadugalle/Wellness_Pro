package com.example.wellness_pro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R

class ReminderTimesAdapter(
    private val reminderTimes: MutableList<String>,
    private val onRemoveClicked: (position: Int) -> Unit
) : RecyclerView.Adapter<ReminderTimesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder_time, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val time = reminderTimes[position]
        holder.textViewReminderTime.text = time
        holder.buttonRemoveTime.setOnClickListener {
            onRemoveClicked(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = reminderTimes.size

    fun addTime(time: String) {
        if (!reminderTimes.contains(time)) { // Prevent duplicates
            reminderTimes.add(time)
            reminderTimes.sort() // Keep the list sorted for consistent display
            notifyItemInserted(reminderTimes.indexOf(time))
        }
    }

    fun removeTime(position: Int) {
        if (position >= 0 && position < reminderTimes.size) {
            reminderTimes.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, reminderTimes.size) // To update subsequent positions
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
