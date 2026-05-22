package com.simcsv.bulksender

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simcsv.bulksender.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val topLevelIds = setOf(
            R.id.dashboardFragment,
            R.id.csvImportFragment,
            R.id.logsFragment,
            R.id.settingsFragment
        )

        val appBarConfig = AppBarConfiguration(topLevelIds)
        setSupportActionBar(binding.toolbar)
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val currentId = navController.currentDestination?.id
            if (currentId == item.itemId) return@setOnItemSelectedListener true

            val options = NavOptions.Builder()
                .setPopUpTo(R.id.dashboardFragment, inclusive = false, saveState = false)
                .setLaunchSingleTop(true)
                .setRestoreState(false)
                .build()

            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (_: Exception) {
                false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val noNavDest = setOf(R.id.previewFragment, R.id.progressFragment)
            binding.bottomNav.visibility = if (destination.id in noNavDest) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }
            if (destination.id in topLevelIds) {
                binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}
