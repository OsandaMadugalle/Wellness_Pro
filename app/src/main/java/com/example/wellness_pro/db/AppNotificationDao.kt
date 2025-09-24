package com.example.wellness_pro.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification)

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): LiveData<List<AppNotification>>

    // You can add other methods like update, delete, etc., as needed
    // For example:
    // @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :notificationId")
    // suspend fun markAsRead(notificationId: String)
    //
    // @Query("DELETE FROM app_notifications")
    // suspend fun clearAll()
}
