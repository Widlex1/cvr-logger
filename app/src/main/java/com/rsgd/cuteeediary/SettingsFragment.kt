package com.rsgd.cuteeediary

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.rsgd.cuteeediary.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var tempSelectedColor: String = "#FF2D7D"

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
        val prefs = requireContext().getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
        
        val isAiEnabled = prefs.getBoolean("ai_enabled", true)
        val customPrompt = prefs.getString("ai_prompt", "You are Gemma, a human-like Ai in a cyberpunk diary. Respond briefly and as naturally as possible to the conversation.")
        val defaultUser = prefs.getString("default_user", "System")
        val uiScale = prefs.getFloat("ui_scale", 1.0f)
        tempSelectedColor = prefs.getString("accent_color", "#FF2D7D") ?: "#FF2D7D"
        val enterIsSend = prefs.getBoolean("enter_is_send", true)

        binding.switchAi.isChecked = isAiEnabled
        binding.switchEnterSend.isChecked = enterIsSend
        binding.etAiPrompt.setText(customPrompt)
        binding.etDefaultUser.setText(defaultUser)
        binding.sliderUiScale.value = uiScale

        setupEnterIsSend(binding.etDefaultUser)
        setupEnterIsSend(binding.etAiPrompt)

        applyTheme(tempSelectedColor)
        setupColorPicker()
    }

    private fun setupEnterIsSend(editText: android.widget.EditText) {
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE)
        editText.setOnEditorActionListener { _, actionId, event ->
            val prefs = requireContext().getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
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
                    setupColorPicker() // Refresh borders
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
        
        // Update Section Headings
        binding.secUser.setTextColor(accentColor)
        binding.secUi.setTextColor(accentColor)
        binding.secEditing.setTextColor(accentColor)
        binding.secAi.setTextColor(accentColor)

        binding.etDefaultUser.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        binding.etAiPrompt.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.sliderUiScale.thumbTintList = colorStateList
        binding.sliderUiScale.trackActiveTintList = colorStateList
        
        binding.switchAi.thumbTintList = colorStateList
        binding.switchAi.trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
        
        binding.switchEnterSend.thumbTintList = colorStateList
        binding.switchEnterSend.trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))

        // v2.0 Fix: Ensure MaterialButton doesn't use default purple tint
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
        val prefs = requireContext().getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
        
        prefs.edit()
            .putBoolean("ai_enabled", binding.switchAi.isChecked)
            .putBoolean("enter_is_send", binding.switchEnterSend.isChecked)
            .putString("ai_prompt", binding.etAiPrompt.text.toString())
            .putString("default_user", binding.etDefaultUser.text.toString())
            .putFloat("ui_scale", binding.sliderUiScale.value)
            .putString("accent_color", tempSelectedColor)
            .apply()

        Toast.makeText(context, "System Reconfigured", Toast.LENGTH_SHORT).show()
        activity?.recreate() 
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}