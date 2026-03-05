package com.rsgd.cvrlogger

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rsgd.cvrlogger.databinding.FragmentThirdBinding
import com.rsgd.cvrlogger.databinding.DialogAddPersonBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

data class Person(var name: String, var color: Int, var isMaster: Boolean = false, var isAI: Boolean = false)
data class MessageInfo(val person: Person, var isStory: Boolean, var originalText: String)

class SparkleSpan(private val color: Int) : CharacterStyle() {
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

class ThirdFragment : Fragment() {

    private var _binding: FragmentThirdBinding? = null
    private val binding get() = _binding!!
    private var lineCount = 1
    private var currentFileName: String? = null
    
    private var people = mutableListOf<Person>()
    private var selectedPersonIndex = 0
    private var editingMessageView: TextView? = null
    private var isStoryMode: Boolean = false

    private val db = FirebaseFirestore.getInstance()
    private var firestoreListener: ListenerRegistration? = null
    private var isUpdatingFromFirestore = false

    private val geminiPerson = Person("Gemma", "#00FFFF".toColorInt(), isMaster = false, isAI = true)

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AIzaSyAxW5ENMcCXBAEZlpsDdTL6wHOpA9ZBGsY"
    )
    
    private val storyTextColor = Color.parseColor("#FFA500")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThirdBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        isStoryMode = prefs.getBoolean("story_mode_enabled", false)

        applyTheme()

        currentFileName = arguments?.getString("fileName")
        
        if (currentFileName != null) {
            binding.tvEditorTitle.text = currentFileName
            loadExistingContent()
        } else {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentFileName = "Log_${timeStamp}.log"
            binding.tvEditorTitle.text = currentFileName
            
            val defaultName = prefs.getString("default_user", "System") ?: "System"
            people.add(Person(defaultName, "#FF2D7D".toColorInt(), isMaster = true))
        }

        startFirestoreSync()
        updateSenderUI()

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tvEditorTitle.setOnClickListener {
            showRenameDialog()
        }

        setupSenderSwipe()

        val isAiEnabled = prefs.getBoolean("ai_enabled", true)
        binding.gemmaArea.visibility = if (isAiEnabled) View.VISIBLE else View.GONE

        binding.btnWakeGemma.setOnClickListener {
            askGemini()
        }

        binding.btnAddPerson.setOnClickListener {
            showAddPersonDialog()
        }

        binding.btnFormatQuote.setOnClickListener {
            isStoryMode = !isStoryMode
            recreateAllMessages()
            saveStoryModeState()
            applyTheme()
        }
        
        binding.btnFormatComment.setOnClickListener {
            showFormattingDialog()
        }

        val isEnterSendEnabled = prefs.getBoolean("enter_is_send", true)
        if (isEnterSendEnabled) {
            binding.etMessageInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    sendMessage()
                    true
                } else false
            }
        }

        binding.btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun showFormattingDialog() {
        val formats = arrayOf("Bold (*text*)", "Italic (_text_)", "Underline (~text~)", "Sparkle (\$text\$)")
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("CHOOSE FORMAT")
            .setItems(formats) { _, which ->
                val wrap = when(which) {
                    0 -> arrayOf("*", "*")
                    1 -> arrayOf("_", "_")
                    2 -> arrayOf("~", "~")
                    3 -> arrayOf("\$", "\$")
                    else -> arrayOf("", "")
                }
                wrapSelection(wrap[0], wrap[1])
            }
            .show()
    }

    private fun wrapSelection(prefix: String, suffix: String) {
        val start = binding.etMessageInput.selectionStart
        val end = binding.etMessageInput.selectionEnd
        val text = binding.etMessageInput.text.toString()
        
        if (start != end) {
            val selected = text.substring(start, end)
            val newText = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
            binding.etMessageInput.setText(newText)
            binding.etMessageInput.setSelection(start + prefix.length, end + prefix.length)
        } else {
            binding.etMessageInput.text.insert(start, prefix + suffix)
            binding.etMessageInput.setSelection(start + prefix.length)
        }
    }

    private fun startFirestoreSync() {
        firestoreListener?.remove()
        val docId = currentFileName?.replace(".", "_") ?: return
        firestoreListener = db.collection("logs").document(docId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("FirestoreSync", "Listen failed: $e")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists() && !isUpdatingFromFirestore) {
                    val messagesData = snapshot.get("messages") as? List<String> ?: emptyList()
                    val peopleData = snapshot.get("people") as? List<String> ?: emptyList()
                    updateSyncData(messagesData, peopleData)
                }
            }
    }

    private fun updateSyncData(messagesData: List<String>, peopleData: List<String>) {
        isUpdatingFromFirestore = true
        
        // Update People
        if (peopleData.isNotEmpty()) {
            val loadedPeople = mutableListOf<Person>()
            peopleData.forEach { line ->
                val p = line.split("|")
                if (p.size >= 3) {
                    loadedPeople.add(Person(p[1], p[0].toInt(), p[2].toBoolean()))
                }
            }
            people = loadedPeople.filter { !it.isAI }.toMutableList()
            if (people.isEmpty()) {
                val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
                val defaultName = prefs.getString("default_user", "System") ?: "System"
                people.add(Person(defaultName, "#FF2D7D".toColorInt(), isMaster = true))
            }
        }

        // Update Messages
        binding.layoutMessages.removeAllViews()
        lineCount = 1
        messagesData.forEach { line ->
            val parts = line.split("|", limit = 6)
            if (parts.size >= 5) {
                val color = parts[0].toIntOrNull() ?: Color.WHITE
                val name = parts[1]
                val isMaster = parts[2].toBoolean()
                val isAI = parts[3].toBoolean()
                val isStoryMessage = parts[4].toBoolean()
                val textContent = parts.last()
                
                val p = Person(name, color, isMaster, isAI)
                val messageInfo = MessageInfo(p, isStoryMessage, textContent)
                addMessageToTerminal(messageInfo, isInitialLoad = true)
            }
        }
        
        updateSenderUI()
        isUpdatingFromFirestore = false
    }

    private fun prefixInput(prefix: String) {
        val currentText = binding.etMessageInput.text.toString()
        val otherPrefix = if (prefix == "> ") "// " else "> "
        var newText = currentText
        if (newText.startsWith(otherPrefix)) newText = newText.removePrefix(otherPrefix)
        
        if (!newText.startsWith(prefix)) binding.etMessageInput.setText(prefix + newText)
        else binding.etMessageInput.setText(newText.removePrefix(prefix))
        
        binding.etMessageInput.setSelection(binding.etMessageInput.text.length)
    }

    private fun sendMessage() {
        val message = binding.etMessageInput.text.toString()
        if (message.isNotBlank()) {
            if (people.isEmpty()) {
                val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
                val defaultName = prefs.getString("default_user", "System") ?: "System"
                people.add(Person(defaultName, "#FF2D7D".toColorInt(), isMaster = true))
                selectedPersonIndex = 0
            }
            
            val currentPerson = people.getOrNull(selectedPersonIndex) ?: people[0]
            
            if (editingMessageView != null) {
                val existingMessageInfo = editingMessageView?.tag as? MessageInfo
                val updatedMessageInfo = existingMessageInfo?.copy(originalText = message) ?: MessageInfo(currentPerson, isStoryMode, message)

                val prefix = if (updatedMessageInfo.person.isMaster) "★ " else ""
                val displayText = if (updatedMessageInfo.isStory) "> ${updatedMessageInfo.originalText}" else "[$prefix${updatedMessageInfo.person.name.uppercase()}] ${updatedMessageInfo.originalText}"
                editingMessageView?.text = formatTerminalText(displayText, updatedMessageInfo.person.color)
                editingMessageView?.tag = updatedMessageInfo
                editingMessageView = null
                binding.btnSend.text = "SEND >"
            } else {
                val newMessageInfo = MessageInfo(currentPerson, isStoryMode, message)
                addMessageToTerminal(newMessageInfo, isInitialLoad = false)
                if (message.lowercase().contains("@gemma")) askGemini()
            }
            saveContent()
            binding.etMessageInput.text.clear()
            binding.tvGuideTerminal.visibility = View.GONE
        }
    }

    private fun formatTerminalText(text: String, color: Int): Spannable {
        val ssb = SpannableStringBuilder(text)
        
        // Bold: *text*
        var match = Regex("\\*(.*?)\\*").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(StyleSpan(Typeface.BOLD), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("\\*(.*?)\\*").find(ssb)
        }

        // Italic: _text_
        match = Regex("_(.*?)_").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(StyleSpan(Typeface.ITALIC), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("_(.*?)_").find(ssb)
        }

        // Underline: ~text~
        match = Regex("~(.*?)~").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(UnderlineSpan(), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("~(.*?)~").find(ssb)
        }

        // Sparkle: $text$
        match = Regex("\\$(.*?)\\$").find(ssb)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            ssb.setSpan(SparkleSpan(color), start + 1, end - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.delete(end - 1, end)
            ssb.delete(start, start + 1)
            match = Regex("\\$(.*?)\\$").find(ssb)
        }

        return ssb
    }

    private fun applyTheme() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        val radius = 12f * resources.displayMetrics.density
        
        fun getNewBorder(thickness: Int = 2, color: Int = accentColor) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(thickness, color)
            cornerRadius = radius
        }
        
        binding.toolbarEditor.background = getNewBorder()
        binding.editorContainer.background = getNewBorder()
        binding.gemmaArea.background = getNewBorder()
        
        binding.senderArea.visibility = View.VISIBLE
        if (isStoryMode) {
            val dullAccent = Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            binding.senderArea.background = getNewBorder(1, dullAccent)
            binding.senderArea.alpha = 0.6f
            binding.senderArea.isClickable = false
        } else {
            binding.senderArea.background = getNewBorder()
            binding.senderArea.alpha = 1.0f
            binding.senderArea.isClickable = true
        }

        binding.formatQuoteArea.background = getNewBorder()
        binding.formatCommentArea.background = getNewBorder()
        binding.inputArea.background = getNewBorder(3)
        
        binding.tvEditorTitle.setTextColor(accentColor)
        binding.btnSend.setTextColor(accentColor)
        
        binding.btnFormatQuote.setTextColor(if (isStoryMode) Color.WHITE else accentColor)
        binding.btnFormatQuote.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (isStoryMode) accentColor else Color.parseColor("#1A1A1A"))
            setStroke(2, accentColor)
            cornerRadius = radius
        }

        binding.btnFormatComment.setTextColor(accentColor)
        binding.btnWakeGemma.setTextColor(accentColor)
        binding.btnAddPerson.setTextColor(accentColor)
        
        binding.btnSend.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor("#1A1A1A".toColorInt())
            setStroke(2, accentColor)
            cornerRadius = radius
        }
        
        updateSenderUI()
    }

    private fun askGemini() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val customPrompt = prefs.getString("ai_prompt", "You are Gemma, a digital assistant in a cyberpunk diary. Respond briefly and helpfuly to the conversation below. Be witty and tech-focused.") ?: ""

        binding.tvStatus.text = "↺ Gemma is thinking..."
        startGeminiIndicator()
        
        val history = mutableListOf<String>()
        for (i in 0 until binding.layoutMessages.childCount) {
            val container = binding.layoutMessages.getChildAt(i) as LinearLayout
            if (container.childCount > 0) {
                val rowLayout = container.getChildAt(0) as LinearLayout
                val messageView = rowLayout.getChildAt(1) as TextView
                val messageInfo = messageView.tag as? MessageInfo
                if (messageInfo != null && !messageInfo.person.isAI) {
                    history.add("${messageInfo.person.name}: ${messageInfo.originalText}")
                }
            }
        }

        val contextPrompt = "$customPrompt\n\nCurrent Conversation History:\n" + history.takeLast(10).joinToString("\n")

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(contextPrompt)
                val aiResponse = response.text ?: "..."
                val aiMessageInfo = MessageInfo(geminiPerson, false, aiResponse)
                addMessageToTerminal(aiMessageInfo)
                saveContent()
            } catch (e: Exception) {
                android.util.Log.e("GeminiError", "Error generating content", e)
                addMessageToTerminal(MessageInfo(geminiPerson, false, "ERROR: Connectivity lost. See Logcat for details."))
            }
            binding.tvStatus.text = "↺ Autosaved"
            stopGeminiIndicator()
        }
    }

    private fun startGeminiIndicator() {
        val blink = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.imgMiniLogo.startAnimation(blink)
        binding.imgMiniLogo.setColorFilter(Color.parseColor("#00FFFF"))
    }

    private fun stopGeminiIndicator() {
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        binding.imgMiniLogo.clearAnimation()
        binding.imgMiniLogo.setColorFilter(accentColor)
    }

    private fun showRenameDialog() {
        val input = EditText(requireContext()).apply {
            setText(currentFileName)
            setSelection(currentFileName?.length ?: 0)
        }
        
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("RENAME SESSION")
            .setView(input)
            .setPositiveButton("UPDATE") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank() && newName != currentFileName) {
                    val oldFile = File(requireContext().filesDir, currentFileName!!)
                    val finalNewName = if (newName.endsWith(".log")) newName else "${newName}.log"
                    val newFile = File(requireContext().filesDir, finalNewName)
                    if (oldFile.renameTo(newFile)) {
                        currentFileName = finalNewName
                        binding.tvEditorTitle.text = currentFileName
                    }
                }
            }
            .setNegativeButton("ABORT", null)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSenderSwipe() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showChoosePersonDialog()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (people.isEmpty()) return false
                if (Math.abs(velocityY) > Math.abs(velocityX)) {
                    if (velocityY > 0) selectedPersonIndex = (selectedPersonIndex + 1) % people.size
                    else selectedPersonIndex = (selectedPersonIndex - 1 + people.size) % people.size
                    updateSenderUI()
                    return true
                }
                return false
            }
        })

        binding.senderArea.setOnTouchListener { _, event ->
            if (binding.senderArea.isClickable) gestureDetector.onTouchEvent(event)
            binding.senderArea.isClickable
        }
    }

    private fun updateSenderUI() {
        if (people.isEmpty()) return
        val p = people.getOrNull(selectedPersonIndex) ?: people[0]
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))
        
        val prefix = if (p.isMaster) "★ " else ""
        binding.tvSenderLabel.setTextColor(accentColor)
        binding.tvSenderName.text = "${prefix}${p.name}"
        binding.tvSenderName.setTextColor(p.color)
        
        val shadowRadius = if (isStoryMode) 0f else 5f
        binding.tvSenderLabel.setShadowLayer(shadowRadius, 0f, 0f, accentColor)
        binding.tvSenderName.setShadowLayer(shadowRadius, 0f, 0f, p.color)
    }

    private fun showChoosePersonDialog() {
        if (!binding.senderArea.isClickable) return
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor("#121212".toColorInt())
            setPadding(32, 32, 32, 32)
        }
        val prefs = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        val title = TextView(requireContext()).apply {
            text = "CHOOSE ENTITY"
            setTextColor(accentColor)
            textSize = 18f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 32)
        }
        dialogView.addView(title)

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setView(dialogView).create()

        people.forEachIndexed { index, person ->
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                isClickable = true
                setOnClickListener {
                    selectedPersonIndex = index
                    updateSenderUI()
                    dialog.dismiss()
                }
            }
            val prefix = if (person.isMaster) "★ " else ""
            val nameView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${prefix}${person.name}"
                setTextColor(person.color)
                textSize = 16f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val editBtn = ImageView(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                setColorFilter(Color.GRAY)
                setPadding(16, 0, 16, 0)
                setOnClickListener {
                    dialog.dismiss()
                    showAddPersonDialog(person)
                }
            }
            itemLayout.addView(nameView)
            if (!person.isAI) itemLayout.addView(editBtn)
            dialogView.addView(itemLayout)
        }
        dialog.show()
    }

    private fun showAddPersonDialog(personToEdit: Person? = null) {
        if (!binding.senderArea.isClickable) return
        val dialogBinding = DialogAddPersonBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()

        if (personToEdit != null) {
            dialogBinding.tvDialogTitle.text = "EDIT ENTITY"
            dialogBinding.etPersonName.setText(personToEdit.name)
            dialogBinding.cbMasterUser.isChecked = personToEdit.isMaster
            dialogBinding.btnConfirm.text = "UPDATE"
        }

        val colors = listOf("#FF2D7D", "#00FFFF", "#00FF00", "#FFFF00", "#FF8C00")
        var localSelectedColor = personToEdit?.color ?: colors[0].toColorInt()

        fun refreshColors() {
            dialogBinding.layoutColorOptions.removeAllViews()
            colors.forEach { colorStr ->
                val colorInt = colorStr.toColorInt()
                val colorView = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(10, 0, 10, 0) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(colorInt)
                        if (localSelectedColor == colorInt) setStroke(6, Color.WHITE)
                        cornerRadius = 12f * resources.displayMetrics.density
                    }
                    setOnClickListener {
                        localSelectedColor = colorInt
                        refreshColors()
                    }
                }
                dialogBinding.layoutColorOptions.addView(colorView)
            }
        }
        refreshColors()

        dialogBinding.btnConfirm.setOnClickListener {
            val name = dialogBinding.etPersonName.text.toString()
            if (name.isNotBlank()) {
                val isNewMaster = dialogBinding.cbMasterUser.isChecked
                if (isNewMaster) people.forEach { it.isMaster = false }
                
                if (personToEdit != null) {
                    personToEdit.name = name
                    personToEdit.color = localSelectedColor
                    personToEdit.isMaster = isNewMaster
                } else {
                    people.add(Person(name, localSelectedColor, isNewMaster))
                    selectedPersonIndex = people.size - 1
                }
                updateSenderUI()
                saveContent()
                dialog.dismiss()
            }
        }
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun loadExistingContent() {
        binding.layoutMessages.removeAllViews()
        lineCount = 1
        val file = File(requireContext().filesDir, currentFileName!!)
        if (file.exists()) {
            val lines = file.readLines()
            val loadedPeople = mutableListOf<Person>()
            lines.forEach { line ->
                val parts = line.split("|", limit = 6)
                if (parts.size >= 5) {
                    val p = Person(parts[1], parts[0].toInt(), parts[2].toBoolean(), parts[3].toBoolean())
                    if (!loadedPeople.any { it.name == p.name }) loadedPeople.add(p)
                    addMessageToTerminal(MessageInfo(p, parts[4].toBoolean(), parts.last()), true)
                }
            }
            if (loadedPeople.isNotEmpty()) people = loadedPeople.filter { !it.isAI }.toMutableList()
        }
        if (people.isEmpty()) {
            val defaultName = requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE).getString("default_user", "System") ?: "System"
            people.add(Person(defaultName, "#FF2D7D".toColorInt(), isMaster = true))
        }
        selectedPersonIndex = 0
        updateSenderUI()
    }

    private fun saveContent() {
        val messageLines = mutableListOf<String>()
        for (i in 0 until binding.layoutMessages.childCount) {
            val row = (binding.layoutMessages.getChildAt(i) as? LinearLayout)?.getChildAt(0) as? LinearLayout
            val tv = row?.getChildAt(1) as? TextView
            val msgInfo = tv?.tag as? MessageInfo ?: continue
            messageLines.add("${msgInfo.person.color}|${msgInfo.person.name}|${msgInfo.person.isMaster}|${msgInfo.person.isAI}|${msgInfo.isStory}|${msgInfo.originalText}")
        }
        
        File(requireContext().filesDir, currentFileName!!).writeText(messageLines.joinToString("\n"))
        binding.tvStatus.text = "↺ Autosaved"
    }

    private fun addMessageToTerminal(messageInfo: MessageInfo, isInitialLoad: Boolean = false) {
        val currentLineNumber = lineCount
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
            setOnClickListener {
                val row = getChildAt(0) as LinearLayout
                val tv = row.getChildAt(1) as TextView
                val msgInfo = tv.tag as? MessageInfo ?: return@setOnClickListener
                if (msgInfo.person.isAI) return@setOnClickListener
                binding.etMessageInput.setText(msgInfo.originalText)
                editingMessageView = tv
                binding.btnSend.text = "UPDATE"
                val idx = people.indexOfFirst { it.name == msgInfo.person.name }
                if (idx != -1) { selectedPersonIndex = idx; updateSenderUI() }
            }
            setOnLongClickListener {
                if (people.getOrNull(selectedPersonIndex)?.isMaster == true) showDeleteLineDialog(this)
                true
            }
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val tvLine = TextView(context).apply {
            text = String.format(Locale.getDefault(), "%02d", currentLineNumber)
            setTextColor("#666666".toColorInt())
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 20, 0)
            visibility = if (messageInfo.isStory) View.GONE else View.VISIBLE
        }
        val tvMsg = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            tag = messageInfo
            val prefix = if (messageInfo.person.isMaster) "★ " else ""
            if (messageInfo.isStory) {
                text = formatTerminalText("> ${messageInfo.originalText}", storyTextColor)
                setTextColor(storyTextColor)
            } else {
                val displayText = "[$prefix${messageInfo.person.name.uppercase()}] ${messageInfo.originalText}"
                text = formatTerminalText(displayText, messageInfo.person.color)
                setTextColor(messageInfo.person.color)
                setShadowLayer(8f, 0f, 0f, messageInfo.person.color)
            }
        }
        row.addView(tvLine); row.addView(tvMsg); container.addView(row)
        binding.layoutMessages.addView(container)
        recalculateLines()
        if (!isInitialLoad) binding.scrollTerminal.post { binding.scrollTerminal.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showDeleteLineDialog(view: View) {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("DELETE LINE").setPositiveButton("DELETE") { _, _ ->
                binding.layoutMessages.removeView(view); recalculateLines(); saveContent()
            }.setNegativeButton("ABORT", null).show()
    }

    private fun recalculateLines() {
        var current = 1
        for (i in 0 until binding.layoutMessages.childCount) {
            val row = (binding.layoutMessages.getChildAt(i) as LinearLayout).getChildAt(0) as LinearLayout
            val tvLine = row.getChildAt(0) as TextView
            val tvMsg = row.getChildAt(1) as TextView
            val info = tvMsg.tag as? MessageInfo
            if (info != null && !info.isStory) {
                tvLine.text = String.format(Locale.getDefault(), "%02d", current)
                tvLine.visibility = View.VISIBLE; current++
            } else tvLine.visibility = View.GONE
        }
        lineCount = current
    }

    private fun recreateAllMessages() {
        val infos = mutableListOf<MessageInfo>()
        for (i in 0 until binding.layoutMessages.childCount) {
            val row = (binding.layoutMessages.getChildAt(i) as LinearLayout).getChildAt(0) as LinearLayout
            val tv = row.getChildAt(1) as TextView
            infos.add(tv.tag as MessageInfo)
        }
        binding.layoutMessages.removeAllViews(); lineCount = 1
        infos.forEach { addMessageToTerminal(it, true) }
        binding.scrollTerminal.post { binding.scrollTerminal.fullScroll(View.FOCUS_DOWN) }
    }

    private fun saveStoryModeState() {
        requireContext().getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE).edit().putBoolean("story_mode_enabled", isStoryMode).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
