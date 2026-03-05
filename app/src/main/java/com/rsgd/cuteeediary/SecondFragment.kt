package com.rsgd.cuteeediary

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.rsgd.cuteeediary.databinding.FragmentSecondBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: NotesAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { importLog(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyGlobalTheme()
        setupRecyclerView()
        
        binding.btnNewNote.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_ThirdFragment)
        }

        // Easter Egg: Press and hold title
        binding.tvHeaderTitle.setOnLongClickListener {
            showEasterEgg()
            true
        }

        binding.btnImportLog.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importLauncher.launch(Intent.createChooser(intent, "Select Log File"))
        }

        binding.btnSettings.setOnClickListener {
            // v2.0 Fix: Navigate to the full-screen fragment instead of a popup
            findNavController().navigate(R.id.action_SecondFragment_to_SettingsFragment)
        }

        binding.btnDelete.setOnClickListener {
            val selectedNotes = adapter.getNotes().filter { it.isSelected }
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("DELETE LOGS")
                .setMessage("Are you sure you want to delete ${selectedNotes.size} log(s)?")
                .setPositiveButton("DELETE") { _, _ ->
                    selectedNotes.forEach { note ->
                        File(requireContext().filesDir, note.fileName).delete()
                    }
                    loadNotes()
                }
                .setNegativeButton("ABORT", null)
                .show()
        }

        binding.btnRename.setOnClickListener {
            val selectedNote = adapter.getNotes().find { it.isSelected }
            if (selectedNote != null) {
                showRenameDialog(selectedNote)
            }
        }
    }

    private fun showEasterEgg() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_easter_egg, null)
        val prefs = requireContext().getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        // Apply theme to the custom easter egg layout
        val title = dialogView.findViewById<View>(R.id.tv_easter_egg_msg) as android.widget.TextView
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_close_secret)
        val lineTop = dialogView.findViewById<View>(R.id.glitch_line_top)
        val lineBottom = dialogView.findViewById<View>(R.id.glitch_line_bottom)

        title.setTextColor(accentColor)
        closeBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(accentColor))
        closeBtn.setTextColor(accentColor)
        lineTop.setBackgroundColor(accentColor)
        lineBottom.setBackgroundColor(accentColor)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyGlobalTheme() {
        val prefs = requireContext().getSharedPreferences("CuteeeDiaryPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        
        binding.headerContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f * resources.displayMetrics.density
        }
        binding.tvHeaderTitle.setTextColor(accentColor)
        binding.btnNewNote.setStrokeColor(android.content.res.ColorStateList.valueOf(accentColor))
        binding.btnNewNote.setTextColor(accentColor)
        binding.btnSettings.setColorFilter(accentColor)
        binding.btnDelete.setColorFilter(accentColor)
        binding.btnRename.setColorFilter(accentColor)
        binding.btnExport.setColorFilter(accentColor)
        
        binding.btnImportLog.setStrokeColor(android.content.res.ColorStateList.valueOf(accentColor))
        binding.btnImportLog.setTextColor(accentColor)
    }

    private fun importLog(uri: android.net.Uri) {
        try {
            var fileName = ""
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            if (!fileName.lowercase().endsWith(".log")) {
                Toast.makeText(requireContext(), "Error: Only .log files are allowed!", Toast.LENGTH_LONG).show()
                return
            }

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val destinationFileName = "Imported_" + System.currentTimeMillis() + ".log"
            val file = File(requireContext().filesDir, destinationFileName)
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            loadNotes()
            Toast.makeText(requireContext(), "Log imported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to import log", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(note: Note) {
        val input = EditText(requireContext()).apply {
            setText(note.fileName)
            setSelection(note.fileName.length)
        }
        
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("RENAME LOG")
            .setView(input)
            .setPositiveButton("CONFIRM") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank() && newName != note.fileName) {
                    val oldFile = File(requireContext().filesDir, note.fileName)
                    val newFile = File(requireContext().filesDir, if (newName.endsWith(".log")) newName else "$newName.log")
                    if (oldFile.renameTo(newFile)) {
                        loadNotes()
                    }
                }
            }
            .setNegativeButton("ABORT", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
        applyGlobalTheme()
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(mutableListOf(), 
            onNoteClick = { note ->
                val bundle = bundleOf("fileName" to note.fileName)
                findNavController().navigate(R.id.action_SecondFragment_to_FourthFragment, bundle)
            },
            onSelectionChanged = { hasSelection ->
                binding.btnDelete.visibility = if (hasSelection) View.VISIBLE else View.GONE
                val selectionCount = adapter.getNotes().count { it.isSelected }
                binding.btnRename.visibility = if (selectionCount == 1) View.VISIBLE else View.GONE
            }
        )

        binding.rvNotes.layoutManager = LinearLayoutManager(context)
        binding.rvNotes.adapter = adapter
    }

    private fun loadNotes() {
        val filesDir = requireContext().filesDir
        val logFiles = filesDir.listFiles { _, name -> name.endsWith(".log") } ?: arrayOf()
        
        val notes = logFiles.map { file ->
            val lastModified = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
            val lines = file.readLines()
            val snippet = lines.lastOrNull()?.split("|")?.lastOrNull() ?: "Empty log..."
            Note(file.name, lastModified, snippet, file.name)
        }.sortedByDescending { File(filesDir, it.fileName).lastModified() }

        adapter.updateNotes(notes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
