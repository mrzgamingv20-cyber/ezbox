package com.mrzgaming.ezbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private var sessions: List<EzSession>,
    private val onLaunch: (EzSession) -> Unit,
    private val onEdit: (EzSession) -> Unit,
    private val onDelete: (EzSession) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.sessionName)
        val details: TextView = view.findViewById(R.id.sessionDetails)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditSession)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteSession)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.name.text = session.name
        holder.details.text = "${session.resolution} • ${session.wineVariant}"
        holder.itemView.setOnClickListener { onLaunch(session) }
        holder.btnEdit.setOnClickListener { onEdit(session) }
        holder.btnDelete.setOnClickListener { onDelete(session) }
    }

    override fun getItemCount() = sessions.size

    fun updateData(newSessions: List<EzSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }
}
