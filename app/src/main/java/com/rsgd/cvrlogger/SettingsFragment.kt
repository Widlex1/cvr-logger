package com.rsgd.cvrlogger

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.rsgd.cvrlogger.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var tempSelectedColor: String = "#FF2D7D"

    private val languages = listOf("English", "Urdu", "Hindi", "Turkish", "Arabic")
    private val languageCodes = listOf("en", "ur", "hi", "tr", "ar")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSettings()

        binding.btnBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        
        val defaultUser = prefs.getString("default_user", "System")
        val uiScale = prefs.getFloat("ui_scale", 1.0f)
        tempSelectedColor = prefs.getString("accent_color", "#FF2D7D") ?: "#FF2D7D"
        val enterIsSend = prefs.getBoolean("enter_is_send", true)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", false)
        val selectedLangCode = prefs.getString("app_language", "en") ?: "en"
        
        val accessPin = prefs.getString("access_pin", "")
        val secretWord = prefs.getString("secret_word", "")

        binding.switchEnterSend.isChecked = enterIsSend
        binding.switchBiometricLock.isChecked = biometricEnabled
        binding.etDefaultUser.setText(defaultUser)
        binding.sliderUiScale.value = uiScale
        
        binding.etAccessPin.setText(accessPin)
        binding.etSecretWord.setText(secretWord)

        // Setup Language Spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        val langIndex = languageCodes.indexOf(selectedLangCode)
        if (langIndex >= 0) binding.spinnerLanguage.setSelection(langIndex)

        setupEnterIsSend(binding.etDefaultUser)

        applyTheme(tempSelectedColor)
        setupColorPicker()
        checkExtensionStatus()
    }

    private fun checkExtensionStatus() {
        val isInstalled = ExtensionManager.isExportExtensionInstalled(requireContext())
        if (isInstalled) {
            binding.tvExtensionStatus.text = "MODULE ACTIVE"
            binding.tvExtensionStatus.setTextColor(Color.parseColor("#00FFFF")) // Cyan for active
        } else {
            binding.tvExtensionStatus.text = "NOT DETECTED"
            val accentColor = Color.parseColor(tempSelectedColor)
            binding.tvExtensionStatus.setTextColor(accentColor)
        }
    }

    private fun setupEnterIsSend(editText: android.widget.EditText) {
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE)
        editText.setOnEditorActionListener { _, actionId, event ->
            val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("enter_is_send", true)
            
            if (isEnabled) {
                if (actionId == EditorInfo.IME_ACTION_DONE || 
                    (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                    saveSettings()
                    true
                } else false
            } else false
        }
    }

    private fun setupColorPicker() {
        val colors = listOf("#FF2D7D", "#00FFFF", "#00FF00", "#FFFF00", "#FF8C00")

        binding.layoutAccentOptions.removeAllViews()
        colors.forEach { colorStr ->
            val colorInt = Color.parseColor(colorStr)
            val colorView = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(10, 0, 10, 0) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(colorInt)
                    if (tempSelectedColor == colorStr) setStroke(4, Color.WHITE)
                    cornerRadius = 12f
                }
                setOnClickListener {
                    tempSelectedColor = colorStr
                    applyTheme(tempSelectedColor)
                    setupColorPicker() 
                    checkExtensionStatus()
                }
            }
            binding.layoutAccentOptions.addView(colorView)
        }
    }

    private fun applyTheme(colorStr: String) {
        val accentColor = Color.parseColor(colorStr)
        val colorStateList = android.content.res.ColorStateList.valueOf(accentColor)

        binding.toolbarSettings.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.tvSettingsTitle.setTextColor(accentColor)
        
        binding.secUser.setTextColor(accentColor)
        binding.secUi.setTextColor(accentColor)
        binding.secEditing.setTextColor(accentColor)
        binding.secSecurity.setTextColor(accentColor)
        binding.secModules.setTextColor(accentColor)

        binding.layoutExtensionStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }

        binding.etDefaultUser.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.etAccessPin.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.etSecretWord.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }

        binding.spinnerLanguage.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.sliderUiScale.thumbTintList = colorStateList
        binding.sliderUiScale.trackActiveTintList = colorStateList
        
        binding.switchEnterSend.thumbTintList = colorStateList
        binding.switchEnterSend.trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))

        binding.switchBiometricLock.thumbTintList = colorStateList
        binding.switchBiometricLock.trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))

        binding.btnSave.backgroundTintList = null 
        binding.btnSave.setTextColor(accentColor)
        binding.btnSave.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1A1A1A"))
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
    }

    private fun saveSettings() {
        val pin = binding.etAccessPin.text.toString()
        val secretWord = binding.etSecretWord.text.toString()

        if (pin.isNotEmpty() && pin.length != 4) {
            TerminalToast.show(requireContext(), "PROTOCOL ERROR: PIN MUST BE 4 DIGITS")
            return
        }

        if (pin.isNotEmpty() && secretWord.isBlank()) {
            TerminalToast.show(requireContext(), "PROTOCOL ERROR: RECOVERY WORD REQUIRED")
            return
        }

        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val selectedLangCode = languageCodes[binding.spinnerLanguage.selectedItemPosition]
        
        prefs.edit()
            .putBoolean("enter_is_send", binding.switchEnterSend.isChecked)
            .putBoolean("biometric_enabled", binding.switchBiometricLock.isChecked)
            .putString("default_user", binding.etDefaultUser.text.toString())
            .putFloat("ui_scale", binding.sliderUiScale.value)
            .putString("accent_color", tempSelectedColor)
            .putString("access_pin", pin)
            .putString("secret_word", secretWord)
            .putString("app_language", selectedLangCode)
            .apply()

        TerminalToast.show(requireContext(), "System Reconfigured")
        activity?.recreate() 
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}