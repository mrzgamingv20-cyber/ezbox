package com.mrzgaming.ezbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AppLibraryFragment : Fragment() {

    private var recyclerApps: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private lateinit var appLibrary: AppLibrary

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app_library, container, false)
        recyclerApps = view.findViewById(R.id.recyclerApps)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        appLibrary = AppLibrary(requireContext())
        loadApps()
        return view
    }

    private fun loadApps() {
        val apps = appLibrary.getInstalledApps()
        if (apps.isEmpty()) {
            recyclerApps?.visibility = View.GONE
            tvEmpty?.visibility = View.VISIBLE
            return
        }
        tvEmpty?.visibility = View.GONE
        recyclerApps?.layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerApps?.adapter = AppAdapter(apps) { app ->
            val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            val vncPassword = prefs.getString("vnc_password", "ezbox123")
            val containerPrefs = requireActivity().getSharedPreferences("EZBoxContainers", Context.MODE_PRIVATE)
            val json = containerPrefs.getString("container_list", null)
            val container = if (!json.isNullOrEmpty()) {
                try {
                    // Ordered array, matching HomeFragment.saveContainers().
                    val list = com.google.gson.Gson().fromJson(json, com.google.gson.JsonArray::class.java)
                    list.firstOrNull()?.asJsonObject
                } catch (e: Exception) { null }
            } else null
            val firstContainer = container?.let { obj ->
                val map = HashMap<String, Any?>()
                obj.entrySet().forEach { e -> map[e.key] = e.value?.asString }
                Container.fromMap(map)
            }

            if (firstContainer != null) {
                val intent = Intent(requireContext(), VncActivity::class.java)
                intent.putExtra("vnc_password", vncPassword)
                intent.putExtra("container_name", firstContainer.name)
                intent.putExtra("container_id", firstContainer.id)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "No container available. Create one from the Home tab.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }
}

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onLaunch: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.appCard)
        val icon: TextView = view.findViewById(R.id.tvAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_library, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.text = app.name.firstOrNull()?.uppercase()?.firstOrNull()?.toString() ?: "?"
        holder.name.text = app.name
        holder.card.setOnClickListener { onLaunch(app) }
    }

    override fun getItemCount() = apps.size
}
