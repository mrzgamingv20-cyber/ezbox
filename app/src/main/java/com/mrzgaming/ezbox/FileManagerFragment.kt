package com.mrzgaming.ezbox

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class FileManagerFragment : Fragment() {

    private var recyclerFiles: RecyclerView? = null
    private var tvPath: TextView? = null
    private var tvEmpty: TextView? = null
    private var fabGoHome: FloatingActionButton? = null
    private var currentPath = "/"
    private val rootPaths = listOf(
        "/data/data/com.termux/files/home",
        "/data/data/com.termux/files/usr",
        Environment.getExternalStorageDirectory().absolutePath,
        "/sdcard",
        "/storage/emulated/0"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_manager, container, false)
        tvPath = view.findViewById(R.id.tvPath)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        recyclerFiles = view.findViewById(R.id.recyclerFiles)
        fabGoHome = view.findViewById(R.id.fabGoHome)

        currentPath = rootPaths[0]
        loadFiles(currentPath)

        fabGoHome?.setOnClickListener {
            currentPath = rootPaths[0]
            loadFiles(currentPath)
        }

        return view
    }

    private fun loadFiles(path: String) {
        currentPath = path
        tvPath?.text = path
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            recyclerFiles?.visibility = View.GONE
            tvEmpty?.visibility = View.VISIBLE
            tvEmpty?.text = "Path not found: $path"
            return
        }
        val files: List<File> = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name }
        ) ?: emptyList()
        if (files.isEmpty()) {
            recyclerFiles?.visibility = View.GONE
            tvEmpty?.visibility = View.VISIBLE
            tvEmpty?.text = "This folder is empty"
            return
        }
        recyclerFiles?.visibility = View.VISIBLE
        tvEmpty?.visibility = View.GONE
        recyclerFiles?.layoutManager = LinearLayoutManager(requireContext())
        recyclerFiles?.adapter = FileAdapter(files) { file ->
            if (file.isDirectory) {
                loadFiles(file.absolutePath)
            } else {
                showFileOptions(file)
            }
        }
    }

    private fun showFileOptions(file: File) {
        val options = mutableListOf<String>()
        options.add("Copy path")
        if (file.canRead()) options.add("View details")
        options.add("Go to containing folder")
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val clip = android.content.ClipData.newPlainText("path", file.absolutePath)
                        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager).setPrimaryClip(clip)
                    }
                    1 -> {
                        val size = if (file.length() > 0) formatSize(file.length()) else "Directory"
                        AlertDialog.Builder(requireContext())
                            .setTitle("Details")
                            .setMessage("Name: ${file.name}\nPath: ${file.absolutePath}\nSize: $size\nModified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(file.lastModified()))}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    2 -> loadFiles(file.parent ?: "/")
                }
            }
            .show()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    override fun onResume() {
        super.onResume()
        loadFiles(currentPath)
    }
}

class FileAdapter(
    private val files: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tvFileIcon)
        val name: TextView = view.findViewById(R.id.tvFileName)
        val info: TextView = view.findViewById(R.id.tvFileInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_manager, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.icon.text = if (file.isDirectory) "📁" else "📄"
        holder.name.text = file.name
        holder.info.text = if (file.isDirectory) "Folder" else formatSizeCompact(file.length())
        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = files.size

    private fun formatSizeCompact(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
