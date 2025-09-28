package com.example.wellness_pro.ui

import android.os.Bundle
import android.view.MenuItem // ADDED for onOptionsItemSelected
import androidx.appcompat.widget.Toolbar // ADDED for Toolbar
import com.example.wellness_pro.R
// import com.example.wellness_pro.navbar.BaseBottomNavActivity // REMOVED
import com.example.wellness_pro.navbar.BaseActivity // CHANGED to BaseActivity

class SettingsActivity : BaseActivity() { // CHANGED parent class

    override val layoutId: Int
        get() = R.layout.activity_settings

    // REMOVED: override val currentNavControllerItemId: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar: Toolbar = findViewById(R.id.toolbar_settings)
        setSupportActionBar(toolbar)

        // Enable the Up button (back arrow)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Specific initialization for SettingsActivity can go here
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
