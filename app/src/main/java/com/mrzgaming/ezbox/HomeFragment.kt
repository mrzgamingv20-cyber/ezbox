package com.mrzgaming.ezbox

import android.app.AlertDialog
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val termuxPermission = "com.termux.permission.RUN_COMMAND"
    private val requestCode = 1001
    private var tvBackendStatus: TextView? = null
    private var tvUptime: TextView? = null
    private var ringRam: RingProgressView? = null
    private var tvRamPercent: TextView? = null
    private var tvRamDetail: TextView? = null
    private var tvGreeting: TextView? = null
    private var fabAdd: FloatingActionButton? = null
    private var recyclerContainers: RecyclerView? = null
    private var containerAdapter: ContainerAdapter? = null
    private var containerList: MutableList<Container> = mutableListOf()

    private val statusPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusPollRunnable: Runnable? = null
    private val launchWaitHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var launchWaitRunnable: Runnable? = null
    private var isWaitingForLaunch = false
    private var isDesktopRunning = false
    private val checkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvBackendStatus = view.findViewById(R.id.tvBackendStatus)
        tvUptime = view.findViewById(R.id.tvUptime)
        ringRam = view.findViewById(R.id.ringRam)
        tvRamPercent = view.findViewById(R.id.tvRamPercent)
        tvRamDetail = view.findViewById(R.id.tvRamDetail)
        recyclerContainers = view.findViewById(R.id.recyclerContainers)
        fabAdd = view.findViewById(R.id.fabAdd)

        setGreeting()
        ringRam?.ringColor = ContextCompat.getColor(requireContext(), R.color.ezos_success)
        loadContainers()
        setupRecycler()
        setupFab()

        return view
    }

    private fun loadContainers() {
        val prefs = requireActivity().getSharedPreferences("EZBoxContainers", Context.MODE_PRIVATE)
        val json = prefs.getString("container_list", null)
        containerList.clear()
        if (json.isNullOrEmpty()) {
            containerList.add(Container.defaultContainer())
            return
        }
        try {
            // Stored as an ordered array, not a map: a HashMap has no order, so
            // containerList[0] (the one performLaunch opens) changed between runs, and
            // two containers sharing an id silently dropped one.
            val list = com.google.gson.Gson().fromJson(json, com.google.gson.JsonArray::class.java)
            for (element in list) {
                val obj = element.asJsonObject
                val map = HashMap<String, Any?>()
                obj.entrySet().forEach { map[it.key] = it.value?.let { v ->
                    if (v.isJsonPrimitive) {
                        when {
                            v.asJsonPrimitive.isBoolean -> v.asBoolean
                            v.asJsonPrimitive.isNumber -> v.asDouble
                            else -> v.asString
                        }
                    } else null
                } }
                containerList.add(Container.fromMap(map))
            }
        } catch (e: Exception) {
            Log.e("EZBox", "Failed to load containers: ${e.message}")
            containerList.clear()
            containerList.add(Container.defaultContainer())
        }
        if (containerList.isEmpty()) {
            containerList.add(Container.defaultContainer())
        }
        containerAdapter?.notifyDataSetChanged()
    }

    private fun saveContainers() {
        val prefs = requireActivity().getSharedPreferences("EZBoxContainers", Context.MODE_PRIVATE)
        // Ordered array, not a map: a HashMap has no order, so containerList[0] (the one
        // performLaunch opens) changed between runs, and two containers sharing an id
        // silently dropped one.
        val array = com.google.gson.JsonArray()
        for (container in containerList) {
            val obj = com.google.gson.JsonObject()
            for ((k, v) in container.toMap()) {
                when (v) {
                    is Number -> obj.addProperty(k, v)
                    is Boolean -> obj.addProperty(k, v)
                    else -> obj.addProperty(k, v.toString())
                }
            }
            array.add(obj)
        }
        prefs.edit().putString("container_list", com.google.gson.Gson().toJson(array)).apply()
    }

    private fun setupRecycler() {
        containerAdapter = ContainerAdapter(containerList) { container, action ->
            when (action) {
                ContainerAction.LAUNCH -> launchContainer(container)
                ContainerAction.EDIT -> showContainerEditor(container)
                ContainerAction.DELETE -> deleteContainer(container)
            }
        }
        recyclerContainers?.layoutManager = LinearLayoutManager(requireContext())
        recyclerContainers?.adapter = containerAdapter
    }

    private fun setupFab() {
        fabAdd?.setOnClickListener {
            val newContainer = Container.defaultContainer()
            containerList.add(0, newContainer)
            saveContainers()
            containerAdapter?.notifyItemInserted(0)
            recyclerContainers?.smoothScrollToPosition(0)
            Toast.makeText(context, "New container created", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchContainer(container: Container) {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val vncPassword = prefs.getString("vnc_password", "ezbox123")
        val resolution = container.resolution
        val de = container.desktopEnvironment

        deployBackendScript()
        val downloadsDir = getDownloadDir()
        downloadsDir?.let { File(it, "ezbox_backend_status.txt").delete() }
        prefs.edit().putLong("desktop_launch_time", System.currentTimeMillis()).apply()

        val statusPath = downloadsDir?.let { File(it, "ezbox_backend_status.txt").absolutePath } ?: "/storage/emulated/0/Download/ezbox_backend_status.txt"
        // A password containing ' would otherwise break out of the quotes and inject
        // arbitrary shell into the Termux command.
        val safePassword = vncPassword.orEmpty().replace("'", "'\\''")
        val command = "echo running > $statusPath; " +
            "EZBOX_RES=$resolution EZBOX_DE=$de EZBOX_VNC_PASSWORD='$safePassword' ezos-run EZOS; " +
            "echo idle > $statusPath"

        TermuxCommand.start(requireContext(), command)
        waitForBackendReady(vncPassword, container)
    }

    private fun waitForBackendReady(vncPassword: String?, container: Container) {
        isWaitingForLaunch = true
        val startTime = System.currentTimeMillis()
        launchWaitRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val elapsed = System.currentTimeMillis() - startTime
                checkExecutor.execute {
                    val ready = try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", 5901), 300)
                        socket.close()
                        true
                    } catch (e: Exception) { false }
                    launchWaitHandler.post { onBackendCheckResult(ready, elapsed, vncPassword, container) }
                }
            }
        }
        launchWaitHandler.post(launchWaitRunnable!!)
    }

    private fun onBackendCheckResult(ready: Boolean, elapsed: Long, vncPassword: String?, container: Container) {
        if (!isAdded) return
        if (ready) {
            isWaitingForLaunch = false
            isDesktopRunning = true
            openDesktop(vncPassword, container)
        } else if (elapsed >= 20000L) {
            isWaitingForLaunch = false
            Toast.makeText(context, "Backend timeout. Try again.", Toast.LENGTH_LONG).show()
        } else {
            launchWaitHandler.postDelayed(launchWaitRunnable!!, 500)
        }
    }

    private fun openDesktop(vncPassword: String?, container: Container) {
        val intent = Intent(requireContext(), VncActivity::class.java)
        intent.putExtra("vnc_password", vncPassword)
        intent.putExtra("container_name", container.name)
        intent.putExtra("container_id", container.id)
        startActivity(intent)
    }

    private fun showContainerEditor(container: Container) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_container_editor)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.BOTTOM)

        val etName = dialog.findViewById<EditText>(R.id.etContainerName)
        val spDe = dialog.findViewById<android.widget.Spinner>(R.id.spDe)
        val spRes = dialog.findViewById<android.widget.Spinner>(R.id.spRes)
        etName.setText(container.name)

        val deOptions = listOf("xfce", "lxqt")
        val resOptions = listOf("960x540", "1280x720", "1600x900")
        spDe.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, deOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spRes.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, resOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spDe.setSelection(deOptions.indexOf(container.desktopEnvironment).coerceAtLeast(0))
        spRes.setSelection(resOptions.indexOf(container.resolution).coerceAtLeast(0))

        dialog.findViewById<Button>(R.id.btnContainerSave)?.setOnClickListener {
            container.name = etName.text.toString().ifBlank { "Container" }
            container.desktopEnvironment = spDe.selectedItem?.toString() ?: "xfce"
            container.resolution = spRes.selectedItem?.toString() ?: "960x540"
            saveContainers()
            containerAdapter?.notifyDataSetChanged()
            dialog.dismiss()
            Toast.makeText(context, "Container updated", Toast.LENGTH_SHORT).show()
        }

        dialog.findViewById<Button>(R.id.btnContainerDelete)?.setOnClickListener {
            deleteContainer(container)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteContainer(container: Container) {
        containerList.remove(container)
        if (containerList.isEmpty()) {
            containerList.add(Container.defaultContainer())
        }
        saveContainers()
        containerAdapter?.notifyDataSetChanged()
        Toast.makeText(context, "Container removed", Toast.LENGTH_SHORT).show()
    }

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        tvGreeting?.text = greeting
    }

    override fun onResume() {
        super.onResume()
        setGreeting()
        loadContainers()
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
        launchWaitRunnable?.let { launchWaitHandler.removeCallbacks(it) }
    }

    private fun startStatusPolling() {
        statusPollRunnable = object : Runnable {
            override fun run() {
                checkBackendStatus()
                statusPollHandler.postDelayed(this, 3000)
            }
        }
        statusPollHandler.post(statusPollRunnable!!)
    }

    private fun getDownloadDir(): File? {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (e: Exception) {
            requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        }
    }

    private fun checkBackendStatus() {
        val downloadsDir = getDownloadDir() ?: return
        val statusFile = File(downloadsDir, "ezbox_backend_status.txt")
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        try {
            if (statusFile.exists()) {
                val content = statusFile.readText().trim()
                if (content == "running") {
                    isDesktopRunning = true
                    tvBackendStatus?.text = "Running"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_success))
                    updateUptime(prefs)
                } else {
                    isDesktopRunning = false
                    tvBackendStatus?.text = "Idle"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                    tvUptime?.visibility = View.GONE
                }
            } else {
                isDesktopRunning = false
                tvBackendStatus?.text = "Idle"
                tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                tvUptime?.visibility = View.GONE
            }
        } catch (e: Exception) {
            tvBackendStatus?.text = "Unknown"
        }
        updateRamUsage()
    }

    private fun updateUptime(prefs: android.content.SharedPreferences) {
        val launchTime = prefs.getLong("desktop_launch_time", 0L)
        if (launchTime == 0L) { tvUptime?.visibility = View.GONE; return }
        val elapsedMs = System.currentTimeMillis() - launchTime
        val minutes = (elapsedMs / 60000) % 60
        val hours = elapsedMs / 3600000
        val text = if (hours > 0) "Running ${hours}h ${minutes}m" else "Running ${minutes}m"
        tvUptime?.text = text
        tvUptime?.visibility = View.VISIBLE
    }

    private fun updateRamUsage() {
        try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            val usedMb = totalMb - availMb
            // totalMb can be 0, which made this -Infinity.toInt() = Int.MIN_VALUE,
            // so the ring rendered "-2147483648%".
            val usedPercent = if (totalMb > 0) ((usedMb.toDouble() / totalMb.toDouble()) * 100).toInt() else 0
            ringRam?.progress = usedPercent
            tvRamPercent?.text = "$usedPercent%"
            tvRamDetail?.text = "${usedMb}MB / ${totalMb}MB used"
        } catch (e: Exception) {
            tvRamDetail?.text = "Unable to read memory"
        }
    }

    private fun deployBackendScript() {
        try {
            val scriptContent = requireContext().assets.open("ezos-run").bufferedReader().readText()
            val deployCommand = "cat << 'EZBOX_SCRIPT_EOF' > \$PREFIX/bin/ezos-run\n${scriptContent}\nEZBOX_SCRIPT_EOF\nchmod +x \$PREFIX/bin/ezos-run"
            TermuxCommand.start(requireContext(), deployCommand)
        } catch (e: Exception) {
            Log.e("EZBox", "Deploy failed: ${e.message}")
        }
    }

    fun performLaunch() {
        if (isDesktopRunning) {
            if (containerList.isNotEmpty()) {
                val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
                val vncPassword = prefs.getString("vnc_password", "ezbox123")
                openDesktop(vncPassword, containerList[0])
            }
        } else {
            checkPermissionAndLaunch()
        }
    }

    private fun checkPermissionAndLaunch() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), termuxPermission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (containerList.isNotEmpty()) {
                launchContainer(containerList[0])
            }
        } else {
            requestPermissions(arrayOf(termuxPermission), requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (containerList.isNotEmpty()) {
                    launchContainer(containerList[0])
                }
            } else {
                Toast.makeText(context, "Termux permission denied. EZBox needs this.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
        launchWaitRunnable?.let { launchWaitHandler.removeCallbacks(it) }
        // Drop view refs, otherwise they outlive the inflated hierarchy.
        tvBackendStatus = null; tvUptime = null; ringRam = null
        tvRamPercent = null; tvRamDetail = null; tvGreeting = null
        fabAdd = null; recyclerContainers = null; containerAdapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // MainActivity builds a new fragment per nav tap, so an unshut executor
        // would leave one idle non-daemon thread behind on every tab switch.
        checkExecutor.shutdownNow()
    }
}

enum class ContainerAction { LAUNCH, EDIT, DELETE }

class ContainerAdapter(
    private val containers: List<Container>,
    private val onAction: (Container, ContainerAction) -> Unit
) : RecyclerView.Adapter<ContainerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvContainerName)
        val de: TextView = view.findViewById(R.id.tvContainerDe)
        val res: TextView = view.findViewById(R.id.tvContainerRes)
        val icon: ImageView = view.findViewById(R.id.ivContainerIcon)
        val btnLaunch: Button = view.findViewById(R.id.btnLaunchContainer)
        val card: View = view.findViewById(R.id.containerCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_container_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val container = containers[position]
        holder.name.text = container.name
        holder.de.text = when (container.desktopEnvironment) { "xfce" -> "XFCE4" else -> "LXQt" }
        holder.res.text = container.resolution

        holder.btnLaunch.setOnClickListener { onAction(container, ContainerAction.LAUNCH) }
        holder.card.setOnLongClickListener {
            onAction(container, ContainerAction.EDIT)
            true
        }
    }

    override fun getItemCount() = containers.size
}
