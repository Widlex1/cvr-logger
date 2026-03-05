package com.rsgd.cuteeediary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.findNavController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: com.rsgd.cuteeediary.databinding.ActivityMainBinding
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyUiScale()

        binding = com.rsgd.cuteeediary.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                val destinationId = navController.currentDestination?.id
                if (destinationId == R.id.SecondFragment) {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finish()
                    } else {
                        Toast.makeText(baseContext, "Swipe back again to exit system", Toast.LENGTH_SHORT).show()
                    }
                    backPressedTime = System.currentTimeMillis()
                } else {
                    navController.popBackStack()
                }
            }
        })

        handleIntent(intent)
    }

    private fun applyUiScale() {
        val prefs = getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
        val scale = prefs.getFloat("ui_scale", 1.0f)
        val configuration = resources.configuration
        configuration.fontScale = scale
        val metrics = resources.displayMetrics
        metrics.scaledDensity = configuration.fontScale * metrics.density
        baseContext.resources.updateConfiguration(configuration, metrics)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_EDIT) {
            val uri: Uri? = intent.data
            uri?.let {
                val fileName = copyFileToInternalStorage(it)
                if (fileName != null) {
                    window.decorView.post {
                        val navController = findNavController(R.id.nav_host_fragment_content_main)
                        val bundle = bundleOf("fileName" to fileName)
                        navController.navigate(R.id.FourthFragment, bundle)
                    }
                }
            }
        }
    }

    private fun copyFileToInternalStorage(uri: Uri): String? {
        try {
            val fileName = "Imported_" + (System.currentTimeMillis() / 1000) + ".log"
            val tempFile = java.io.File(filesDir, fileName)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                java.io.FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return fileName
        } catch (e: Exception) {
            return null
        }
    }
}