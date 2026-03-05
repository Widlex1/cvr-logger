package com.rsgd.cvrlogger

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.rsgd.cvrlogger.databinding.ActivityMainBinding
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "en") ?: "en"
        val uiScale = prefs.getFloat("ui_scale", 1.0f)
        
        val context = applySettings(newBase, langCode, uiScale)
        super.attachBaseContext(context)
    }

    private fun applySettings(context: Context, lang: String, scale: Float): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        // Apply font scale for text
        config.fontScale = scale
        
        // Note: For a full interface scale (affecting margins/paddings/dimensions),
        // we can attempt to modify the densityDpi. 
        // However, fontScale is the safest way to provide "UI Scale" in a fragment-based app
        // without breaking layout constraints.

        return context.createConfigurationContext(config)
    }
}

class MainActivity : BaseActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.hide()

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
