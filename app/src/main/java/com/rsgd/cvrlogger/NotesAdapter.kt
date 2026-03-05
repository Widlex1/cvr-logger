package com.rsgd.cvrlogger

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rsgd.cvrlogger.databinding.ItemNoteBinding

class NotesAdapter(
    private var notes: List<Note>,
    private val onNoteClick: (Note) -> Unit,
    private val onSelectionChanged: (Boolean) -> Unit
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

        holder.binding.tvNoteTitle.text = note.title
        holder.binding.tvNoteDate.text = note.date
        holder.binding.tvNoteSnippet.text = note.snippet
        
        // Dynamic color for log name
        holder.binding.tvNoteTitle.setTextColor(accentColor)

        if (note.isSelected) {
            holder.binding.root.setStrokeColor(ColorStateList.valueOf(accentColor))
            // Transparent version of accent color for background
            val semiTransparent = Color.argb(50, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            holder.binding.root.setCardBackgroundColor(semiTransparent)
        } else {
            holder.binding.root.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#333333")))
            holder.binding.root.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        holder.itemView.setOnClickListener {
            if (notes.any { it.isSelected }) {
                toggleSelection(position)
            } else {
                onNoteClick(note)
            }
        }

        holder.itemView.setOnLongClickListener {
            toggleSelection(position)
            true
        }
    }

    private fun toggleSelection(position: Int) {
        notes[position].isSelected = !notes[position].isSelected
        notifyItemChanged(position)
        onSelectionChanged(notes.any { it.isSelected })
    }

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    fun deleteSelected() {
        notes = notes.filter { !it.isSelected }
        notifyDataSetChanged()
        onSelectionChanged(false)
    }

    fun getNotes() = notes

    override fun getItemCount() = notes.size
}