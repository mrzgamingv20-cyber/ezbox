package com.mrzgaming.ezbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _backendStatus = MutableLiveData<String>("Idle")
    val backendStatus: LiveData<String> = _backendStatus

    private val _isDesktopRunning = MutableLiveData(false)
    val isDesktopRunning: LiveData<Boolean> = _isDesktopRunning

    private val _uptimeText = MutableLiveData("")
    val uptimeText: LiveData<String> = _uptimeText

    private val _ramPercent = MutableLiveData(0)
    val ramPercent: LiveData<Int> = _ramPercent

    private val _ramDetail = MutableLiveData("")
    val ramDetail: LiveData<String> = _ramDetail

    private val _launchProgress = MutableLiveData<String?>(null)
    val launchProgress: LiveData<String?> = _launchProgress

    fun setBackendRunning(running: Boolean) {
        _isDesktopRunning.value = running
        _backendStatus.value = if (running) "Running" else "Idle"
    }

    fun setUptime(text: String) {
        _uptimeText.value = text
    }

    fun setRam(percent: Int, detail: String) {
        _ramPercent.value = percent
        _ramDetail.value = detail
    }

    fun setLaunchProgress(message: String?) {
        _launchProgress.value = message
    }

    fun clearLaunchProgress() {
        _launchProgress.value = null
    }

    override fun onCleared() {
        super.onCleared()
    }
}
