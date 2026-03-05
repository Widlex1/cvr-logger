package com.rsgd.cuteeediary

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.rsgd.cuteeediary.databinding.FragmentFourthBinding
import java.io.File

class FourthFragment : Fragment() {

    private var _binding: FragmentFourthBinding? = null
    private val binding get() = _binding!!
    
    private val storyTextColor = Color.parseColor("#FFA500") // Fixed orange color for story mode text

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
                binding.tvReadTitle.text = fileName.uppercase()
                
                val lines = file.readLines()
                
                // Prepare the scrollable layout structure
                val scrollLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                }
                
                // Find the index of tvReadContent to replace it correctly
                val tvReadContentParent = binding.tvReadContent.parent as ViewGroup
                val tvReadContentIndex = tvReadContentParent.indexOfChild(binding.tvReadContent)
                
                // Remove old TextView
                tvReadContentParent.removeView(binding.tvReadContent)
                
                lines.forEachIndexed { index, line ->
                    // Updated split logic based on ThirdFragment's save format:
                    // color|name|isMaster|isAI|isStory|textContent (6 parts, last part is textContent)
                    val parts = line.split("|", limit = 6)
                    
                    if (parts.size >= 6) {
                        val color = parts[0].toIntOrNull() ?: Color.WHITE
                        val name = parts[1]
                        val isMaster = parts[2].toBoolean()
                        val isAI = parts[3].toBoolean()
                        val isStory = parts[4].toBoolean() // Read isStory flag
                        val textContent = parts.last()
                        
                        val tvMessage = TextView(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 20, 0, 20)
                            }
                            textSize = 14f
                            typeface = android.graphics.Typeface.MONOSPACE
                            
                            val prefix = if (isMaster) "★ " else ""

                            if (isStory) {
                                text = "> $textContent"
                                setTextColor(storyTextColor)
                                setShadowLayer(0f, 0f, 0f, 0) // No shadow on story text
                            } else {
                                text = "[$prefix${name.uppercase()}]: $textContent"
                                setTextColor(color)
                                // Use Float for radius as per ThirdFragment's working implementation
                                setShadowLayer(5f, 0f, 0f, color) 
                            }
                        }
                        scrollLayout.addView(tvMessage)
                        
                        // Add separator only if it's not a story message
                        if (!isStory && index < lines.size - 1) {
                            val separator = View(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                                setBackgroundColor(Color.parseColor("#333333"))
                            }
                            scrollLayout.addView(separator)
                        }
                    }
                }
                
                // Replace the simple TextView with our new scrollLayout
                tvReadContentParent.addView(scrollLayout, tvReadContentIndex)
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
        val prefs = requireContext().getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        
        binding.readContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 4f
        }
        binding.tvReadTitle.setTextColor(accentColor)
        
        // Assuming MaterialButton: set backgroundTintList to transparent to show only the stroke/text color
        binding.btnEditLog.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        binding.btnEditLog.setTextColor(accentColor)
        
        binding.btnExportLog.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        binding.btnExportLog.setTextColor(accentColor)
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
            
            // v2.0: Export as .txt instead of .log
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