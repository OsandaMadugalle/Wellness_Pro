package com.example.wellness_pro.ui

import android.os.Bundle
import android.view.MenuItem // ADDED for onOptionsItemSelected
import androidx.appcompat.widget.Toolbar // ADDED for Toolbar
import com.example.wellness_pro.R
// import com.example.wellness_pro.navbar.BaseBottomNavActivity // REMOVED
import com.example.wellness_pro.navbar.BaseActivity // CHANGED to BaseActivity
import android.widget.TextView
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.text.InputType
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import android.app.Activity
import android.util.Log
import android.os.Process
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.content.pm.PackageManager
import androidx.work.WorkManager
import androidx.core.app.NotificationManagerCompat
import android.appwidget.AppWidgetManager
import java.io.File
import com.example.wellness_pro.reminders.HydrationReminderManager
import com.example.wellness_pro.reminders.HabitReminderManager

class SettingsActivity : BaseActivity() { // CHANGED parent class

	override val layoutId: Int
		get() = R.layout.activity_settings

	private val prefsName = "AppSettingsPrefs"

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val toolbar: Toolbar = findViewById(R.id.toolbar_settings)
		setSupportActionBar(toolbar)

		// Enable the Up button (back arrow)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setDisplayShowHomeEnabled(true)
		toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

		// Apply top inset so toolbar isn't hidden under status bar
		ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
			val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
			v.updatePadding(top = statusBars.top)
			insets
		}
		// Keep toolbar above content
		toolbar.elevation = resources.getDimension(R.dimen.toolbar_elevation_default)
		toolbar.bringToFront()

		initSettingsUI()
	}

	private fun initSettingsUI() {
		val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

		// Version
		findViewById<TextView?>(R.id.textViewVersionValue)?.text = try {
			val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
				packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
			} else {
				@Suppress("DEPRECATION")
				packageManager.getPackageInfo(packageName, 0)
			}
			val vName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pInfo.longVersionCode.toString() else pInfo.versionName
			pInfo.versionName ?: vName ?: "1.0"
		} catch (e: Exception) { "1.0" }

		// Notifications
		val switchEnableNotifications = findViewById<Switch?>(R.id.switchEnableNotifications)
		switchEnableNotifications?.isChecked = prefs.getBoolean("app_notifications_enabled", true)
		switchEnableNotifications?.setOnCheckedChangeListener { _, isChecked ->
			prefs.edit().putBoolean("app_notifications_enabled", isChecked).apply()
			if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				try {
					startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
						putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
					})
				} catch (e: Exception) {
					Toast.makeText(this, "Enable notifications in system settings if needed.", Toast.LENGTH_SHORT).show()
				}
			}
		}

		// Dark mode
		val switchDarkMode = findViewById<Switch?>(R.id.switchDarkMode)
		switchDarkMode?.isChecked = prefs.getBoolean("dark_mode_enabled", false)
		applyDarkMode(prefs.getBoolean("dark_mode_enabled", false))
		switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
			prefs.edit().putBoolean("dark_mode_enabled", isChecked).apply()
			applyDarkMode(isChecked)
		}

		// Shake-to-add-mood settings
		val switchShakeQuickMood = findViewById<Switch?>(R.id.switchShakeQuickMood)
		val seekBarShakeSensitivity = findViewById<android.widget.SeekBar?>(R.id.seekBarShakeSensitivity)
		val textViewShakeSensitivityValue = findViewById<TextView?>(R.id.textViewShakeSensitivityValue)

		// Defaults
		val defaultEnabled = prefs.getBoolean("shake_quick_mood_enabled", true)
		val defaultSensitivity = prefs.getFloat("shake_sensitivity", 2.7f)

		// Map stored sensitivity (float) to SeekBar position (0..50)
		fun sensitivityToProgress(s: Float): Int {
			val clipped = s.coerceIn(0.5f, 5.5f)
			return ((clipped - 0.5f) / 0.1f).toInt()
		}

		fun progressToSensitivity(p: Int): Float {
			return 0.5f + p * 0.1f
		}

		switchShakeQuickMood?.isChecked = defaultEnabled
		seekBarShakeSensitivity?.progress = sensitivityToProgress(defaultSensitivity)
		textViewShakeSensitivityValue?.text = "Current: ${"%.1f".format(defaultSensitivity)}g"

		switchShakeQuickMood?.setOnCheckedChangeListener { _, isChecked ->
			prefs.edit().putBoolean("shake_quick_mood_enabled", isChecked).apply()
		}

		seekBarShakeSensitivity?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
				val sens = progressToSensitivity(progress)
				textViewShakeSensitivityValue?.text = "Current: ${"%.1f".format(sens)}g"
				prefs.edit().putFloat("shake_sensitivity", sens).apply()
			}

			override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
			override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
		})

		// Change display name
		findViewById<Button?>(R.id.buttonChangeName)?.setOnClickListener {
			showChangeNameDialog()
		}

		// Reset app - destructive action
		findViewById<Button?>(R.id.buttonResetApp)?.setOnClickListener {
			AlertDialog.Builder(this)
				.setTitle(getString(R.string.settings_reset_confirm_title))
				.setMessage(getString(R.string.settings_reset_confirm_message))
				.setPositiveButton(getString(R.string.settings_reset_confirm_positive)) { _, _ ->
					performAppReset()
				}
				.setNegativeButton(getString(R.string.settings_reset_confirm_negative), null)
				.show()
		}
	}

	private fun applyDarkMode(enabled: Boolean) {
		AppCompatDelegate.setDefaultNightMode(
			if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
		)
	}

	private fun showChangeNameDialog() {
		val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
		val currentName = prefs.getString("display_name", "") ?: ""
		val input = EditText(this).apply {
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
			setText(currentName)
		}
		AlertDialog.Builder(this)
			.setTitle("Change display name")
			.setView(input)
			.setPositiveButton("Save") { _, _ ->
				val newName = input.text?.toString()?.trim() ?: ""
				prefs.edit().putString("display_name", newName).apply()
				Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	// ADDED: Handle Up button press
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		if (item.itemId == android.R.id.home) {
			onBackPressedDispatcher.onBackPressed() // or finish()
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	/**
	 * Clear app preferences and restart launcher activity.
	 */
	private fun performAppReset() {
		// Clear known SharedPreferences files used by the app
		Toast.makeText(this, "Resetting app...", Toast.LENGTH_SHORT).show()
		Log.i("SettingsActivity", "performAppReset: starting reset")

		// Clear known SharedPreferences files used by the app
		try {
			val appPrefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
			appPrefs.edit().clear().apply()
			val habitsPrefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
			habitsPrefs.edit().clear().apply()
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed clearing named prefs", e)
		}

		// Also clear default/shared preference file (if used by PreferenceFragmentCompat)
		try {
			val defaultPrefsName = "${'$'}{packageName}_preferences"
			val defaultPrefs = getSharedPreferences(defaultPrefsName, Context.MODE_PRIVATE)
			defaultPrefs.edit().clear().apply()
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed clearing default prefs", e)
		}

		// Cancel alarms and WorkManager scheduled work (best-effort)
		try {
			Log.i("SettingsActivity", "performAppReset: cancelling hydration alarms")
			HydrationReminderManager.cancelAllReminders(this)
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed cancelling alarms", e)
		}

		try {
			Log.i("SettingsActivity", "performAppReset: cancelling WorkManager work")
			val wm = WorkManager.getInstance(applicationContext)
			// Cancel known unique work names used by the app
			try { wm.cancelUniqueWork("HydrationReminderWork") } catch (_: Exception) {}
			try { wm.cancelAllWork() } catch (_: Exception) {}
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed cancelling WorkManager work", e)
		}

		// Clear notifications
		try {
			Log.i("SettingsActivity", "performAppReset: clearing notifications")
			NotificationManagerCompat.from(this).cancelAll()
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed clearing notifications", e)
		}

		// Remove app widgets data if present (they read from SharedPreferences which we clear above)
		try {
			Log.i("SettingsActivity", "performAppReset: notifying app widgets to update")
			val appWidgetManager = AppWidgetManager.getInstance(this)
			val ids = appWidgetManager.getAppWidgetIds(componentName)
			if (ids != null && ids.isNotEmpty()) {
				appWidgetManager.notifyAppWidgetViewDataChanged(ids, android.R.id.list)
			}
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed updating widgets", e)
		}

		// Remove cache and files
		try {
			cacheDir.deleteRecursively()
			filesDir.deleteRecursively()
			// External storage areas (best-effort)
			try { externalCacheDir?.deleteRecursively() } catch (_: Exception) {}
			try { getExternalFilesDir(null)?.deleteRecursively() } catch (_: Exception) {}
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed deleting cache/files", e)
		}

		// Delete app databases if any
		try {
			// Also attempt to delete the Room DB by name used in AppDatabase
			val roomDbName = "wellness_pro_database"
			Log.i("SettingsActivity", "Attempting to delete Room DB: $roomDbName")
			// Cancel WorkManager first to avoid jobs touching DB while we delete it
			try { WorkManager.getInstance(applicationContext).cancelAllWork() } catch (_: Exception) {}
			deleteDatabase(roomDbName)

			for (dbName in databaseList()) {
				Log.i("SettingsActivity", "Deleting database: $dbName")
				deleteDatabase(dbName)
			}
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed deleting databases", e)
		}

		// Try to remove the shared_prefs XML files directly (best-effort)
		try {
			val dataDir = applicationInfo.dataDir
			val prefsDir = File(dataDir, "shared_prefs")
			if (prefsDir.exists() && prefsDir.isDirectory) {
				prefsDir.listFiles()?.forEach { f ->
					try {
						Log.i("SettingsActivity", "Deleting prefs file: ${f.name}")
						f.delete()
					} catch (e: Exception) {
						Log.w("SettingsActivity", "Could not delete prefs file: ${f.name}", e)
					}
				}
			}
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed deleting shared_prefs files", e)
		}

		// Also attempt to cancel habit reminders (if any)
		try {
			Log.i("SettingsActivity", "performAppReset: cancelling habit reminders (best-effort)")
			// We don't have a list of habit IDs here; canceling will be best-effort via shared prefs clearing
			// If app stores habit IDs, consider iterating and calling HabitReminderManager.cancelHabitReminder(context, id)
		} catch (e: Exception) {
			Log.w("SettingsActivity", "performAppReset: failed cancelling habit reminders", e)
		}

		// Restart app by launching the launcher activity and clearing task
		val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
		if (launchIntent != null) {
			// Ensure all activities are finished
			try {
				if (this is Activity) {
					finishAffinity()
				}
			} catch (e: Exception) {
				// ignore
			}

			launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
			startActivity(launchIntent)
			Log.i("SettingsActivity", "performAppReset: restart intent started")

			// Ensure a fresh process so Room/SQLite will recreate schema correctly
			try {
				Thread.sleep(300)
			} catch (e: InterruptedException) {
				// ignore
			}
			try {
				Process.killProcess(Process.myPid())
			} catch (e: Exception) {
				Log.w("SettingsActivity", "performAppReset: failed to kill process", e)
			}
			try {
				System.exit(0)
			} catch (e: Exception) {
				// best-effort
			}
		} else {
			Log.w("SettingsActivity", "performAppReset: no launch intent available")
			Toast.makeText(this, "App reset complete. Please restart the app.", Toast.LENGTH_LONG).show()
		}
	}
}
