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
import android.content.pm.PackageManager

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

		// Change display name
		findViewById<Button?>(R.id.buttonChangeName)?.setOnClickListener {
			showChangeNameDialog()
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
}
