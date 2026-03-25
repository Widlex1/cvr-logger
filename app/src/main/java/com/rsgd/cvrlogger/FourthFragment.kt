package com.rsgd.cvrlogger

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rsgd.cvrlogger.databinding.FragmentFourthBinding
import com.rsgd.cvrlogger.databinding.DialogInfoBinding
import java.io.File
import java.util.Locale
import java.util.Random
import android.content.Intent

class SparkleSpanRead(private val color: Int) : CharacterStyle() {
    private val random = Random()
    override fun updateDrawState(tp: android.text.TextPaint) {
        if (random.nextBoolean()) {
            tp.color = color
            tp.isFakeBoldText = true
        } else {
            tp.color = Color.WHITE
        }
    }
}

class FourthFragment : Fragment() {

    private var _binding: FragmentFourthBinding? = null
    private val binding get() = _binding!!
    
    private val storyTextColor = Color.parseColor("#FFA500")
    private var currentFileName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFourthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyTheme()

        currentFileName = arguments?.getString("fileName") ?: ""
        
        if (currentFileName.isNotEmpty()) {
            val file = File(requireContext().filesDir, currentFileName)
            if (file.exists()) {
                binding.tvReadTitle.text = currentFileName.substringBeforeLast(".")
                
                val lines = file.readLines()
                binding.layoutReadMessages.removeAllViews()
                
                var displayIndex = 1
                lines.forEach { line ->
                    if (line.startsWith("ENTITY|")) return@forEach
                    
                    val parts: List<String>
                    val color: Int
                    val name: String
                    val isMaster: Boolean
                    val isAI: Boolean
                    var isStory: Boolean
                    val textContent: String

                    if (line.startsWith("MSG|")) {
                        parts = line.split("|", limit = 7)
                        if (parts.size < 7) return@forEach
                        color = parts[1].toIntOrNull() ?: Color.WHITE
                        name = parts[2]
                        isMaster = parts[3].toBoolean()
                        isAI = parts[4].toBoolean()
                        isStory = parts[5].toBoolean()
                        textContent = parts.last()
                    } else {
                        // Backward compatibility
                        parts = line.split("|", limit = 6)
                        if (parts.size < 5) return@forEach
                        color = parts[0].toIntOrNull() ?: Color.WHITE
                        name = parts[1]
                        isMaster = parts[2].toBoolean()
                        isAI = parts[3].toBoolean()
                        isStory = parts[4].toBoolean() 
                        textContent = if (parts.size >= 6) parts.last() else ""
                    }
                    
                    val finalIsStory: Boolean
                    val finalText: String
                    if (textContent.trim().startsWith(">")) {
                        finalIsStory = true
                        finalText = textContent.trim().substring(1).trim()
                    } else {
                        finalIsStory = isStory
                        finalText = textContent
                    }
                    
                    val tvMessage = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 8, 0, 8)
                        }
                        textSize = 14f
                        typeface = android.graphics.Typeface.MONOSPACE
                        
                        val prefix = if (isMaster) "★ " else ""

                        if (finalIsStory) {
                            text = formatTerminalText("> $finalText", storyTextColor)
                            setTextColor(storyTextColor)
                        } else {
                            val lineNumber = String.format(Locale.US, "%02d", displayIndex++)
                            val messageText = "[$prefix${name.uppercase()}] $finalText"
                            
                            val row = LinearLayout(requireContext()).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            }
                            
                            val messageView = TextView(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                text = formatTerminalText(messageText, color)
                                setTextColor(color)
                                textSize = 14f
                                typeface = android.graphics.Typeface.MONOSPACE
                                if (isAI) {
                                    setShadowLayer(10f, 0f, 0f, color)
                                }
                            }
                            
                            val numberView = TextView(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                text = lineNumber
                                setTextColor(Color.parseColor("#444444"))
                                textSize = 10f
                                typeface = android.graphics.Typeface.MONOSPACE
                                setPadding(16, 0, 0, 0)
                            }
                            
                            row.addView(messageView)
                            row.addView(numberView)
                            binding.layoutReadMessages.addView(row)
                            return@apply 
                        }
                    }
                    if (tvMessage.parent == null) {
                        binding.layoutReadMessages.addView(tvMessage)
                    }
                }
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnEditLog.setOnClickListener {
            val bundle = bundleOf("fileName" to currentFileName)
            findNavController().navigate(R.id.action_FourthFragment_to_ThirdFragment, bundle)
        }

        binding.btnExportLog.setOnClickListener {
            handleExportInitiation()
        }
    }

    private fun handleExportInitiation() {
        if (ExtensionManager.isExportExtensionInstalled(requireContext())) {
            handleModuleExport(currentFileName)
        } else {
            val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
            val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
            
            val infoBinding = DialogInfoBinding.inflate(layoutInflater)
            val infoDialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
                .setView(infoBinding.root)
                .create()
            
            infoBinding.tvDialogTitle.text = "MODULE MISSING"
            infoBinding.tvDialogTitle.setTextColor(accentColor)
            infoBinding.tvDialogMsg.text = "Extraction Module not detected. Please install the required extension to export logs, or use the Main screen (Long-press) to save as .cvrg."
            infoBinding.btnOk.setTextColor(accentColor)
            infoBinding.btnOk.setOnClickListener { infoDialog.dismiss() }
            infoDialog.show()
        }
    }

    private fun handleModuleExport(fileName: String) {
        val logFile = File(requireContext().filesDir, fileName)
        val logContent = if (logFile.exists()) logFile.readText() else ""
        val intent = ExtensionManager.getExportIntent(fileName, logContent)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            TerminalToast.show(requireContext(), "CRITICAL: UPLINK FAILED")
        }
    }

    private fun formatTerminalText(text: String, color: Int): Spannable {
        val ssb = SpannableStringBuilder(text)
        
        var match = Regex("\\*(.*?)\\*").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(StyleSpan(Typeface.BOLD), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("\\*(.*?)\\*").find(ssb)
        }

        match = Regex("_(.*?)_").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(StyleSpan(Typeface.ITALIC), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("_(.*?)_").find(ssb)
        }

        match = Regex("~(.*?)~").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(UnderlineSpan(), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("~(.*?)~").find(ssb)
        }

        match = Regex("\\$(.*?)\\$").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(SparkleSpanRead(color), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("\\$(.*?)\\$").find(ssb)
        }

        return ssb
    }

    private fun applyTheme() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        
        binding.toolbarRead.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.readContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f
        }
        
        binding.tvReadTitle.setTextColor(accentColor)
        binding.tvMiniAppName.setTextColor(accentColor)
        
        binding.btnEditLog.setStrokeColor(android.content.res.ColorStateList.valueOf(accentColor))
        binding.btnEditLog.setTextColor(accentColor)
        
        binding.btnExportLog.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FFFF")))
        binding.btnExportLog.setTextColor(Color.parseColor("#00FFFF"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
