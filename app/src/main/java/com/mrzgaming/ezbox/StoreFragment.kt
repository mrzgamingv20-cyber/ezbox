package com.mrzgaming.ezbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class StoreFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val packageList = mutableListOf<Pair<String, String>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_store, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = PackageAdapter(packageList)
        
        loadPackageList()
        
        return view
    }

    private fun loadPackageList() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    URL("https://raw.githubusercontent.com/mrzgamingv20-cyber/ezos-repo/main/packages/index.json").readText()
                }
                val jsonObject = JSONObject(json)
                packageList.clear()
                jsonObject.keys().forEach { key ->
                    packageList.add(key to jsonObject.getString(key))
                }
                recyclerView.adapter?.notifyDataSetChanged()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    class PackageAdapter(private val list: List<Pair<String, String>>) : RecyclerView.Adapter<PackageAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.packageName)
            val desc: TextView = view.findViewById(R.id.packageDescription)
            val install: Button = view.findViewById(R.id.installButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_package, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.name.text = item.first
            holder.desc.text = item.second
            holder.install.setOnClickListener { /* TODO: Implement install logic */ }
        }

        override fun getItemCount() = list.size
    }
}