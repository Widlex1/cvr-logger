package com.rsgd.cvrlogger

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.rsgd.cvrlogger.databinding.CustomToastBinding

object TerminalToast {
    fun show(context: Context, message: String) {
        val prefs = context.getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        
        val inflater = LayoutInflater.from(context)
        val binding = CustomToastBinding.inflate(inflater)
        
        binding.toastText.text = message
        binding.toastText.setTextColor(accentColor)
        
        binding.toastLayout.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            setColor(Color.parseColor("#1A1A1A"))
            cornerRadius = 8f * context.resources.displayMetrics.density
        }

        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = binding.root
        toast.show()
    }
}