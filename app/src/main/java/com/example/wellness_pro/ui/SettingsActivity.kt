package com.example.wellness_pro.ui

import android.os.Bundle
import com.example.wellness_pro.R
import com.example.wellness_pro.navbar.BaseBottomNavActivity

class SettingsActivity : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_settings

    // If Settings will be a main navigation item, specify its ID here.
    // If not, you might remove this or handle it differently.
    override val currentNavControllerItemId: Int
        get() = R.id.navButtonSettings // Assuming you have/will have an ID 'navButtonSettings' in your menu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Specific initialization for SettingsActivity can go here
        // The layout is set by BaseBottomNavActivity using layoutId
    }
}
