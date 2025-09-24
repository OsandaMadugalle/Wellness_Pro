package com.example.wellness_pro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R // Assuming item layout will be in main R
import com.example.wellness_pro.db.AppNotification // Assuming this is your Notification data class

class AppNotificationAdapter(
    private var notifications: List<AppNotification>,
    private val onItemClicked: (AppNotification) -> Unit
) : RecyclerView.Adapter<AppNotificationAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        // ASSUMPTION: You will create a layout file named 'item_notification.xml'
        // in your res/layout folder.
        // This layout should contain at least a TextView with id 'textViewNotificationTitle'.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false) // Ensure item_notification.xml exists
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
        // ASSUMPTION: Your item_notification.xml has a TextView with this ID.
        private val titleTextView: TextView = itemView.findViewById(R.id.textViewNotificationTitle)

        fun bind(notification: AppNotification, onItemClicked: (AppNotification) -> Unit) {
            titleTextView.text = notification.title
            itemView.setOnClickListener {
                onItemClicked(notification)
            }
        }
    }
}
