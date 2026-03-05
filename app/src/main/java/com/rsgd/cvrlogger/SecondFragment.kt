package com.rsgd.cvrlogger

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.rsgd.cvrlogger.databinding.FragmentSecondBinding
import com.rsgd.cvrlogger.databinding.DialogLogOptionsBinding
import com.rsgd.cvrlogger.databinding.DialogRenameBinding
import com.rsgd.cvrlogger.databinding.DialogAuthBinding
import com.rsgd.cvrlogger.databinding.DialogRecoveryBinding
import com.rsgd.cvrlogger.databinding.DialogInfoBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

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
            findNavController().navigate(R.id.action_SecondFragment_to_SettingsFragment)
        }
    }

    private fun showLogOptionsDialog(note: Note) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        val dialogBinding = DialogLogOptionsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogTitle.setTextColor(accentColor)
        dialogBinding.btnLock.text = if (note.isLocked) "UNLOCK LOG" else "LOCK LOG"
        dialogBinding.btnLock.setTextColor(accentColor)
        dialogBinding.btnRename.setTextColor(accentColor)
        dialogBinding.btnDelete.setTextColor(accentColor)
        dialogBinding.btnExport.setTextColor(accentColor)

        dialogBinding.btnRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(note)
        }

        dialogBinding.btnLock.setOnClickListener {
            if (note.isLocked) {
                showAuthDialog(note) {
                    note.isLocked = false
                    saveLockState(note)
                    adapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
            } else {
                note.isLocked = true
                saveLockState(note)
                adapter.notifyDataSetChanged()
                dialog.dismiss()
                Toast.makeText(context, "LOG ENCRYPTED", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnDelete.setOnClickListener {
            val confirmBinding = DialogInfoBinding.inflate(layoutInflater)
            val confirmDialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
                .setView(confirmBinding.root)
                .create()
            
            confirmBinding.tvDialogTitle.text = "DELETE LOG"
            confirmBinding.tvDialogTitle.setTextColor(accentColor)
            confirmBinding.tvDialogMsg.text = "Are you sure you want to permanently delete this log?"
            confirmBinding.btnOk.text = "DELETE"
            confirmBinding.btnOk.setTextColor(accentColor)
            confirmBinding.btnOk.setOnClickListener {
                File(requireContext().filesDir, note.fileName).delete()
                loadNotes()
                confirmDialog.dismiss()
                dialog.dismiss()
            }
            confirmDialog.show()
        }

        dialogBinding.btnExport.setOnClickListener {
            Toast.makeText(context, "EXPORTING DATA...", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAuthDialog(note: Note, onAuthenticated: () -> Unit) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", true)
        
        val biometricManager = BiometricManager.from(requireContext())
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        
        if (biometricEnabled && canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(requireContext())
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        showPinAuthDialog(note, onAuthenticated)
                    }
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("AUTHENTICATION REQUIRED")
                .setSubtitle("Use fingerprint or device lock to decrypt")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            showPinAuthDialog(note, onAuthenticated)
        }
    }

    private fun showPinAuthDialog(note: Note, onAuthenticated: () -> Unit) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        val correctPin = prefs.getString("access_pin", "") ?: ""
        val secretWord = prefs.getString("secret_word", "") ?: ""

        if (correctPin.isEmpty()) {
            Toast.makeText(context, "NO PIN SET IN SETTINGS", Toast.LENGTH_SHORT).show()
            onAuthenticated() 
            return
        }

        val authBinding = DialogAuthBinding.inflate(layoutInflater)
        val authDialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
            .setView(authBinding.root)
            .create()

        authBinding.tvDialogTitle.setTextColor(accentColor)
        authBinding.etAuthPin.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f * resources.displayMetrics.density
        }
        authBinding.btnConfirm.setTextColor(accentColor)
        authBinding.btnForgotPin.setTextColor(Color.parseColor("#00FFFF"))

        authBinding.btnConfirm.setOnClickListener {
            val enteredPin = authBinding.etAuthPin.text.toString()
            if (enteredPin == correctPin) {
                authDialog.dismiss()
                onAuthenticated()
            } else {
                Toast.makeText(context, "ACCESS DENIED: INVALID PIN", Toast.LENGTH_SHORT).show()
            }
        }

        authBinding.btnCancel.setOnClickListener { authDialog.dismiss() }

        authBinding.btnForgotPin.setOnClickListener {
            authDialog.dismiss()
            showRecoveryDialog(secretWord, correctPin)
        }

        authDialog.show()
    }

    private fun showRecoveryDialog(correctWord: String, pinToReveal: String) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        val recoveryBinding = DialogRecoveryBinding.inflate(layoutInflater)
        val recoveryDialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
            .setView(recoveryBinding.root)
            .create()

        recoveryBinding.tvDialogTitle.setTextColor(accentColor)
        recoveryBinding.etRecoveryWord.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f * resources.displayMetrics.density
        }
        recoveryBinding.btnVerify.setTextColor(accentColor)

        recoveryBinding.btnVerify.setOnClickListener {
            if (recoveryBinding.etRecoveryWord.text.toString() == correctWord) {
                recoveryDialog.dismiss()
                val infoBinding = DialogInfoBinding.inflate(layoutInflater)
                val infoDialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
                    .setView(infoBinding.root)
                    .create()
                
                infoBinding.tvDialogTitle.text = "PROTOCOL BYPASSED"
                infoBinding.tvDialogTitle.setTextColor(accentColor)
                infoBinding.tvDialogMsg.text = "YOUR ACCESS PIN IS: $pinToReveal"
                infoBinding.btnOk.setTextColor(accentColor)
                infoBinding.btnOk.setOnClickListener { infoDialog.dismiss() }
                infoDialog.show()
            } else {
                Toast.makeText(context, "VERIFICATION FAILED", Toast.LENGTH_SHORT).show()
            }
        }

        recoveryBinding.btnAbort.setOnClickListener { recoveryDialog.dismiss() }
        recoveryDialog.show()
    }

    private fun saveLockState(note: Note) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerLocks", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(note.fileName, note.isLocked).apply()
    }

    private fun loadLockState(fileName: String): Boolean {
        val prefs = requireContext().getSharedPreferences("CVRLoggerLocks", Context.MODE_PRIVATE)
        return prefs.getBoolean(fileName, false)
    }

    private fun showRenameDialog(note: Note) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        val dialogBinding = DialogRenameBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogTitle.setTextColor(accentColor)
        dialogBinding.etSessionName.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f * resources.displayMetrics.density
        }
        dialogBinding.btnUpdate.setTextColor(accentColor)

        val nameWithoutExt = note.fileName.removeSuffix(".log")
        dialogBinding.etSessionName.setText(nameWithoutExt)
        dialogBinding.etSessionName.setSelection(nameWithoutExt.length)

        dialogBinding.btnAbort.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnUpdate.setOnClickListener {
            val newName = dialogBinding.etSessionName.text.toString()
            if (newName.isNotBlank() && newName != nameWithoutExt) {
                val oldFile = File(requireContext().filesDir, note.fileName)
                val finalNewName = if (newName.endsWith(".log")) newName else "${newName}.log"
                val newFile = File(requireContext().filesDir, finalNewName)
                if (oldFile.renameTo(newFile)) {
                    loadNotes()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Rename failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEasterEgg() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_easter_egg, null)
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        val cardRoot = dialogView as? MaterialCardView
        val title = dialogView.findViewById<TextView>(R.id.tv_easter_egg_msg)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_close_secret)
        val scanline = dialogView.findViewById<View>(R.id.view_scanline)

        cardRoot?.strokeColor = accentColor
        title?.setTextColor(accentColor)
        closeBtn?.setStrokeColor(android.content.res.ColorStateList.valueOf(accentColor))
        closeBtn?.setTextColor(accentColor)
        scanline?.setBackgroundColor(accentColor)

        val dialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
            .setView(dialogView)
            .create()

        scanline?.post {
            val parentHeight = (scanline.parent as View).height.toFloat()
            ObjectAnimator.ofFloat(scanline, "translationY", -20f, parentHeight).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyGlobalTheme() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
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
                Toast.makeText(requireContext(), "Error: Only .log files are allowed!", Toast.LENGTH_SHORT).show()
                return
            }

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val destFile = File(requireContext().filesDir, fileName)
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            loadNotes()
            Toast.makeText(requireContext(), "Log imported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(mutableListOf(), 
            onNoteClick = { note ->
                val navigate = {
                    val bundle = bundleOf("fileName" to note.fileName)
                    findNavController().navigate(R.id.action_SecondFragment_to_FourthFragment, bundle)
                }

                if (note.isLocked) {
                    showAuthDialog(note) { navigate() }
                } else {
                    navigate()
                }
            },
            onNoteLongClick = { note ->
                showLogOptionsDialog(note)
            }
        )
        binding.rvNotes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotes.adapter = adapter
        loadNotes()
    }

    private fun loadNotes() {
        val files = requireContext().filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val notes = files.map { file ->
            val lastLine = try {
                file.useLines { it.lastOrNull() ?: "" }
            } catch (e: Exception) {
                ""
            }

            val snippet = if (lastLine.contains("|")) {
                lastLine.substringAfterLast("|")
            } else {
                lastLine
            }
            
            Note(
                title = file.name.removeSuffix(".log"),
                date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                snippet = snippet,
                fileName = file.name,
                isLocked = loadLockState(file.name)
            )
        }
        adapter.updateNotes(notes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
