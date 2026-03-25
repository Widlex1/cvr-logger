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
import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import android.net.Uri

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: NotesAdapter
    private var exportingFileName: String = ""

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { importLog(it) }
        }
    }

    private val exportCvrgLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { saveCvrgToUri(it) }
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

        checkFirstRun()
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
            importLauncher.launch(Intent.createChooser(intent, "Select Log File (.log or .cvrg)"))
        }

        binding.btnSettings.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
            val pin = prefs.getString("access_pin", "") ?: ""
            val biometricEnabled = prefs.getBoolean("biometric_enabled", false)
            
            val biometricManager = BiometricManager.from(requireContext())
            val canBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

            // Trigger authentication if a PIN is set OR if biometric is enabled and available
            if (pin.isNotEmpty() || (biometricEnabled && canBiometric)) {
                showAuthDialog("AUTHORIZATION REQUIRED FOR SYSTEM ACCESS") {
                    findNavController().navigate(R.id.action_SecondFragment_to_SettingsFragment)
                }
            } else {
                findNavController().navigate(R.id.action_SecondFragment_to_SettingsFragment)
            }
        }
    }

    private fun checkFirstRun() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("first_run_v4", true)
        if (isFirstRun) {
            createWelcomeLog()
            prefs.edit().putBoolean("first_run_v4", false).apply()
        }
    }

    private fun createWelcomeLog() {
        val welcomeFile = File(requireContext().filesDir, "Welcome_Log.log")
        
        // Remove old version if it exists to show updated instructions
        if (welcomeFile.exists()) welcomeFile.delete()

        val accentColor = Color.parseColor("#FF2D7D")
        val cyanColor = Color.parseColor("#00FFFF")

        val content = StringBuilder().apply {
            append("ENTITY|$accentColor|System|true|false\n")
            append("ENTITY|$cyanColor|Eltyra|false|true\n")
            append("MSG|$accentColor|System|true|false|false|[09:00] WELCOME TO CVR LOGGER TERMINAL.\n")
            append("MSG|$cyanColor|Eltyra|false|true|false|[09:01] I am Eltyra, your AI interface. Let me guide you through the protocol.\n")
            append("MSG|$accentColor|System|true|false|false|[09:02] *ENTITIES*: Personas representing log participants. Create new ones via the + icon in the editor.\n")
            append("MSG|$cyanColor|Eltyra|false|true|false|[09:03] Swipe or tap the sender area to switch between active entities.\n")
            append("MSG|$accentColor|System|true|false|false|[09:04] *MASTER ENTITIES*: Marked with a ★. Only they have the clearance to long-press and DELETE log entries.\n")
            append("MSG|$cyanColor|Eltyra|false|true|false|[09:05] *D&T*: Toggle the [D&T] button to enable or disable automatic timestamps in your session.\n")
            append("MSG|$accentColor|System|true|false|false|[09:06] *MODIFICATION*: Tap any existing entry to edit its text or reassign it to a different entity.\n")
            append("MSG|$accentColor|System|true|false|false|[09:07] *STORY MODE*: Toggle for narrative logs, or start a line with `>` for a quick entry.\n")
            append("MSG|$accentColor|System|true|false|false|[09:08] *FORMATTING*: Select text to apply _italic_, *bold*, ~underline~, or \$sparkle\$ effects.\n")
            append("MSG|$accentColor|System|true|false|false|[09:09] *SECURITY*: Set a 4-digit PIN in Settings to protect your logs and system configuration.\n")
            append("MSG|$cyanColor|Eltyra|false|true|false|[09:10] *EXTENSIONS*: Install the 'CVR Export Module' to enable log extraction to external storage.\n")
            append("MSG|$accentColor|System|true|false|false|[09:11] *CVRG PROTOCOL*: Use the Main screen (Long-press) to export .cvrg (Encrypted) files without any external module.")
        }.toString()

        welcomeFile.writeText(content)
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
        dialogBinding.btnSaveCvrg.setTextColor(accentColor)

        dialogBinding.btnRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(note)
        }

        dialogBinding.btnLock.setOnClickListener {
            if (note.isLocked) {
                showAuthDialog {
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
                TerminalToast.show(requireContext(), "LOG ENCRYPTED")
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

        dialogBinding.btnSaveCvrg.setOnClickListener {
            dialog.dismiss()
            startCvrgExport(note.fileName)
        }

        dialogBinding.btnExport.setOnClickListener {
            dialog.dismiss()
            handleExport(note.fileName)
        }

        dialog.show()
    }

    private fun startCvrgExport(fileName: String) {
        exportingFileName = fileName
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/xml"
            putExtra(Intent.EXTRA_TITLE, fileName.substringBeforeLast(".") + ".cvrg")
        }
        exportCvrgLauncher.launch(intent)
    }

    private fun saveCvrgToUri(uri: Uri) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                val xmlContent = generateXmlContent(exportingFileName)
                val encryptedData = encryptData(xmlContent)
                outputStream.write(encryptedData)
                TerminalToast.show(requireContext(), "CVRG DATA ENCRYPTED & SAVED")
            }
        } catch (e: Exception) {
            TerminalToast.show(requireContext(), "EXPORT FAILED: ${e.message}")
        }
    }

    private fun generateXmlContent(fileName: String): String {
        val file = File(requireContext().filesDir, fileName)
        val lines = file.readLines()
        
        val serializer = Xml.newSerializer()
        val writer = java.io.StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "CVRLog")
        
        lines.forEach { line ->
            if (line.startsWith("ENTITY|")) {
                val parts = line.split("|")
                if (parts.size >= 5) {
                    serializer.startTag(null, "Entity")
                    serializer.attribute(null, "color", parts[1])
                    serializer.attribute(null, "name", parts[2])
                    serializer.attribute(null, "isMaster", parts[3])
                    serializer.attribute(null, "isAI", parts[4])
                    serializer.endTag(null, "Entity")
                }
            } else if (line.startsWith("MSG|")) {
                val parts = line.split("|", limit = 7)
                if (parts.size >= 7) {
                    serializer.startTag(null, "Message")
                    serializer.attribute(null, "color", parts[1])
                    serializer.attribute(null, "name", parts[2])
                    serializer.attribute(null, "isMaster", parts[3])
                    serializer.attribute(null, "isAI", parts[4])
                    serializer.attribute(null, "isStory", parts[5])
                    serializer.attribute(null, "text", parts.last())
                    serializer.endTag(null, "Message")
                }
            }
        }
        
        serializer.endTag(null, "CVRLog")
        serializer.endDocument()
        return writer.toString()
    }

    private fun encryptData(xml: String): ByteArray {
        val data = xml.toByteArray(Charsets.UTF_8)
        val key = "CVR_PROTOCOL_ALPHA".toByteArray()
        val encrypted = ByteArray(data.size)
        for (i in data.indices) {
            encrypted[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return Base64.encode(encrypted, Base64.DEFAULT)
    }

    private fun handleExport(fileName: String) {
        if (ExtensionManager.isExportExtensionInstalled(requireContext())) {
            val logFile = File(requireContext().filesDir, fileName)
            val logContent = if (logFile.exists()) logFile.readText() else ""
            val intent = ExtensionManager.getExportIntent(fileName, logContent)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                TerminalToast.show(requireContext(), "CRITICAL: UPLINK FAILED")
            }
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

    private fun showAuthDialog(message: String? = null, onAuthenticated: () -> Unit) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", false)
        val pin = prefs.getString("access_pin", "") ?: ""
        
        val biometricManager = BiometricManager.from(requireContext())
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        
        if (biometricEnabled && canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(requireContext())
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Fallback to PIN if user cancels biometric or it fails
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || 
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_LOCKOUT || 
                        errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        showPinAuthDialog(message, onAuthenticated)
                    }
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("AUTHENTICATION REQUIRED")
                .setSubtitle(message ?: "Use biometric to proceed")
                .setNegativeButtonText(if (pin.isNotEmpty()) "USE PIN" else "CANCEL")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            showPinAuthDialog(message, onAuthenticated)
        }
    }

    private fun showPinAuthDialog(message: String? = null, onAuthenticated: () -> Unit) {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        val correctPin = prefs.getString("access_pin", "") ?: ""
        val secretWord = prefs.getString("secret_word", "") ?: ""

        if (correctPin.isEmpty()) {
            onAuthenticated() 
            return
        }

        val authBinding = DialogAuthBinding.inflate(layoutInflater)
        val authDialog = AlertDialog.Builder(requireContext(), R.style.CyberDialog)
            .setView(authBinding.root)
            .setCancelable(false)
            .create()

        if (message != null) {
            authBinding.tvAuthMsg.text = message
        }

        authBinding.tvDialogTitle.setTextColor(accentColor)
        authBinding.etAuthPin.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(2, accentColor)
            cornerRadius = 12f * resources.displayMetrics.density
        }
        authDialog.setOnShowListener {
            authDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
            authDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
        }
        authBinding.btnConfirm.setTextColor(accentColor)
        authBinding.btnForgotPin.setTextColor(Color.parseColor("#00FFFF"))

        authBinding.btnConfirm.setOnClickListener {
            val enteredPin = authBinding.etAuthPin.text.toString()
            if (enteredPin == correctPin) {
                authDialog.dismiss()
                onAuthenticated()
            } else {
                TerminalToast.show(requireContext(), "ACCESS DENIED: INVALID PIN")
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
                TerminalToast.show(requireContext(), "VERIFICATION FAILED")
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

        val nameWithoutExt = note.fileName.substringBeforeLast(".")
        dialogBinding.etSessionName.setText(nameWithoutExt)
        dialogBinding.etSessionName.setSelection(nameWithoutExt.length)

        dialogBinding.btnAbort.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnUpdate.setOnClickListener {
            val newName = dialogBinding.etSessionName.text.toString()
            if (newName.isNotBlank() && newName != nameWithoutExt) {
                val oldFile = File(requireContext().filesDir, note.fileName)
                val ext = note.fileName.substringAfterLast(".", "log")
                val finalNewName = if (newName.endsWith(".$ext")) newName else "${newName}.$ext"
                val newFile = File(requireContext().filesDir, finalNewName)
                if (oldFile.renameTo(newFile)) {
                    loadNotes()
                    dialog.dismiss()
                } else {
                    TerminalToast.show(requireContext(), "Rename failed")
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

            if (!fileName.lowercase().endsWith(".log") && !fileName.lowercase().endsWith(".cvrg")) {
                TerminalToast.show(requireContext(), "Error: Only .log or .cvrg files allowed!")
                return
            }

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                if (fileName.lowercase().endsWith(".cvrg")) {
                    val encryptedData = inputStream.readBytes()
                    val decodedXml = decryptData(encryptedData)
                    val logContent = convertCvrgToLog(decodedXml)
                    val internalName = fileName.replace(".cvrg", ".log")
                    val destFile = File(requireContext().filesDir, internalName)
                    destFile.writeText(logContent)
                } else {
                    val destFile = File(requireContext().filesDir, fileName)
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            loadNotes()
            TerminalToast.show(requireContext(), "Log imported successfully")
        } catch (e: Exception) {
            TerminalToast.show(requireContext(), "Import failed: ${e.message}")
        }
    }

    private fun decryptData(data: ByteArray): String {
        val encrypted = Base64.decode(data, Base64.DEFAULT)
        val key = "CVR_PROTOCOL_ALPHA".toByteArray()
        val decrypted = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            decrypted[i] = (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }

    private fun convertCvrgToLog(xml: String): String {
        val logContent = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Entity" -> {
                        val color = parser.getAttributeValue(null, "color")
                        val name = parser.getAttributeValue(null, "name")
                        val isMaster = parser.getAttributeValue(null, "isMaster")
                        val isAI = parser.getAttributeValue(null, "isAI")
                        logContent.append("ENTITY|$color|$name|$isMaster|$isAI\n")
                    }
                    "Message" -> {
                        val color = parser.getAttributeValue(null, "color")
                        val name = parser.getAttributeValue(null, "name")
                        val isMaster = parser.getAttributeValue(null, "isMaster")
                        val isAI = parser.getAttributeValue(null, "isAI")
                        val isStory = parser.getAttributeValue(null, "isStory")
                        val text = parser.getAttributeValue(null, "text")
                        logContent.append("MSG|$color|$name|$isMaster|$isAI|$isStory|$text\n")
                    }
                }
            }
            eventType = parser.next()
        }
        return logContent.toString()
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(mutableListOf(), 
            onNoteClick = { note ->
                val navigate = {
                    val bundle = bundleOf("fileName" to note.fileName)
                    findNavController().navigate(R.id.action_SecondFragment_to_FourthFragment, bundle)
                }

                if (note.isLocked) {
                    showAuthDialog { navigate() }
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
            file.isFile && (file.name.endsWith(".log") || file.name.endsWith(".cvrg"))
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
                title = file.name.substringBeforeLast("."),
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