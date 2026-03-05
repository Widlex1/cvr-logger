package com.rsgd.cvrlogger

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rsgd.cvrlogger.databinding.FragmentFourthBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FourthFragment : Fragment() {

    private var _binding: FragmentFourthBinding? = null
    private val binding get() = _binding!!
    
    private val storyTextColor = Color.parseColor("#FFA500") 

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

        val fileName = arguments?.getString("fileName") ?: ""
        
        if (fileName.isNotEmpty()) {
            val file = File(requireContext().filesDir, fileName)
            if (file.exists()) {
                binding.tvReadTitle.text = fileName
                
                val lastModified = Date(file.lastModified())
                val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
                // We don't have tv_read_date in the new layout, but we could add it back if needed.
                // For now, let's just focus on the messages.

                val lines = file.readLines()
                binding.layoutReadMessages.removeAllViews()
                
                lines.forEachIndexed { index, line ->
                    val parts = line.split("|", limit = 6)
                    
                    if (parts.size >= 6) {
                        val color = parts[0].toIntOrNull() ?: Color.WHITE
                        val name = parts[1]
                        val isMaster = parts[2].toBoolean()
                        val isAI = parts[3].toBoolean()
                        val isStory = parts[4].toBoolean() 
                        val textContent = parts.last()
                        
                        val tvMessage = TextView(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 8, 0, 8)
                            }
                            textSize = 14f
                            typeface = android.graphics.Typeface.MONOSPACE
                            
                            val prefix = if (isMaster) "★ " else ""

                            if (isStory) {
                                text = "> $textContent"
                                setTextColor(storyTextColor)
                            } else {
                                val lineNumber = String.format(Locale.US, "%02d", index + 1)
                                val messageText = "[$prefix${name.uppercase()}] $textContent"
                                
                                // Create a horizontal layout for the message and the line number
                                val row = LinearLayout(requireContext()).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                }
                                
                                val messageView = TextView(requireContext()).apply {
                                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                    text = messageText
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
                                    setTextColor(Color.parseColor("#444444")) // Dim gray for line numbers
                                    textSize = 10f
                                    typeface = android.graphics.Typeface.MONOSPACE
                                    setPadding(16, 0, 0, 0)
                                }
                                
                                row.addView(messageView)
                                row.addView(numberView)
                                binding.layoutReadMessages.addView(row)
                                return@forEachIndexed
                            }
                        }
                        binding.layoutReadMessages.addView(tvMessage)
                    }
                }
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnEditLog.setOnClickListener {
            val bundle = bundleOf("fileName" to fileName)
            findNavController().navigate(R.id.action_FourthFragment_to_ThirdFragment, bundle)
        }

        binding.btnExportLog.setOnClickListener {
            exportLog(fileName)
        }
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

    private fun exportLog(fileName: String) {
        val file = File(requireContext().filesDir, fileName)
        if (file.exists()) {
            val cleanContent = StringBuilder()
            file.readLines().forEachIndexed { index, line ->
                val parts = line.split("|", limit = 6)
                if (parts.size >= 6) {
                    val name = parts[1]
                    val isMaster = parts[2].toBoolean()
                    val isStory = parts[4].toBoolean()
                    val textContent = parts.last()

                    val contentLine = if (isStory) {
                        "> $textContent"
                    } else {
                        val prefix = if (isMaster) "★ " else ""
                        "[$prefix${name.uppercase()}]: $textContent"
                    }
                    cleanContent.append(String.format("%02d %s\n", index + 1, contentLine))
                }
            }
            
            val exportFileName = fileName.replace(".log", ".txt")
            val exportFile = File(requireContext().cacheDir, exportFileName)
            exportFile.writeText(cleanContent.toString())

            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", exportFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export Log as .TXT"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}