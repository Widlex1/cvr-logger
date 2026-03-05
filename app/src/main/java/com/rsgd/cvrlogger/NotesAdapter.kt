package com.rsgd.cvrlogger

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rsgd.cvrlogger.databinding.ItemNoteBinding

class NotesAdapter(
    private var notes: List<Note>,
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    class NoteViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        val context = holder.itemView.context
        val prefs = context.getSharedPreferences("CVRLoggerPrefs", Context.MODE_PRIVATE)
        val accentColor = Color.parseColor(prefs.getString("accent_color", "#FF2D7D"))

        holder.binding.tvNoteTitle.text = note.title.removeSuffix(".log")
        holder.binding.tvNoteDate.text = note.date
        holder.binding.tvNoteSnippet.text = if (note.isLocked) context.getString(R.string.label_encrypted_data) else note.snippet
        
        holder.binding.tvNoteTitle.setTextColor(accentColor)

        if (note.isLocked) {
            holder.binding.root.setStrokeColor(ColorStateList.valueOf(Color.RED))
            holder.binding.tvNoteTitle.append(" 🔒")
        } else {
            holder.binding.root.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#333333")))
        }

        holder.binding.root.setCardBackgroundColor(Color.parseColor("#1A1A1A"))

        holder.itemView.setOnClickListener {
            onNoteClick(note)
        }

        holder.itemView.setOnLongClickListener {
            onNoteLongClick(note)
            true
        }
    }

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    fun getNotes() = notes

    override fun getItemCount() = notes.size
}