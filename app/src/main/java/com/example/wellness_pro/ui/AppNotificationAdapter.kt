package com.example.wellness_pro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppNotificationAdapter(
    private var notifications: List<AppNotification>,
    private val onItemClicked: (AppNotification) -> Unit
) : RecyclerView.Adapter<AppNotificationAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification, onItemClicked)
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newNotifications: List<AppNotification>) {
        notifications = newNotifications
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.textViewNotificationTitle)
        private val dateTextView: TextView = itemView.findViewById(R.id.textViewNotificationDate)
        private val timeTextView: TextView = itemView.findViewById(R.id.textViewNotificationTime)

        // Date and Time formatters
        private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(notification: AppNotification, onItemClicked: (AppNotification) -> Unit) {
            titleTextView.text = notification.title
            
            // Convert timestamp to Date object
            val dateObject = Date(notification.timestamp)
            
            dateTextView.text = dateFormatter.format(dateObject)
            timeTextView.text = timeFormatter.format(dateObject)
            
            itemView.setOnClickListener {
                onItemClicked(notification)
            }
        }
    }
}
