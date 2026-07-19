package com.patrollink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrollink.data.RuntimeDependencyFactory
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.navigation.PatrolApp

class MainActivity : ComponentActivity() {
    private var bluetoothEnabled by mutableStateOf(false)
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshBluetoothState()
    }
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            bluetoothEnabled = when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON -> true
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> false
                else -> readBluetoothEnabled()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerBluetoothStateReceiver()
        refreshBluetoothState()
        setContent {
            val viewModel: PatrolViewModel = viewModel(
                factory = PatrolViewModelFactory(this@MainActivity)
            )
            PatrolApp(
                viewModel = viewModel,
                bluetoothEnabled = bluetoothEnabled,
                onToggleBluetooth = ::toggleBluetooth
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBluetoothState()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(bluetoothStateReceiver) }
        super.onDestroy()
    }

    private fun refreshBluetoothState() {
        bluetoothEnabled = readBluetoothEnabled()
    }

    private fun readBluetoothEnabled(): Boolean {
        return runCatching { bluetoothManager.adapter?.isEnabled == true }.getOrDefault(false)
    }

    private fun registerBluetoothStateReceiver() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothStateReceiver, filter)
        }
    }

    private fun toggleBluetooth() {
        val adapterAvailable = runCatching { bluetoothManager.adapter != null }.getOrDefault(false)
        if (!adapterAvailable || bluetoothEnabled) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } else {
            runCatching {
                bluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }
    }
}

private class PatrolViewModelFactory(
    private val activity: ComponentActivity
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val dependencies = RuntimeDependencyFactory.create(activity.applicationContext)
        return PatrolViewModel(
            appContext = activity.applicationContext,
            coordinator = dependencies.coordinator,
            deviceControlGateway = dependencies.deviceControlGateway,
            secureStore = dependencies.secureStore,
            settingsStore = dependencies.settingsStore,
            locationGateway = dependencies.locationGateway,
            sosEvidenceRecorder = dependencies.sosEvidenceRecorder,
            notificationGateway = dependencies.notificationGateway,
            versionGateway = dependencies.versionGateway,
            firmwareGateway = dependencies.firmwareGateway,
            versionInstaller = dependencies.versionInstaller,
            cerebellumApi = dependencies.cerebellumApi,
            patrolRestApi = dependencies.patrolRestApi,
            runtimeConfigStore = dependencies.configStore,
            backendBaseUrl = dependencies.config.restBaseUrl,
            offlineSyncEngine = dependencies.offlineSyncEngine,
            onSessionChanged = dependencies.tokenStore::update,
            onPairingUsernameChanged = dependencies.tokenStore::updatePairingUsername,
            onSelectedDeviceChanged = dependencies.onSelectedDeviceChanged,
            currentLocalAccountProvider = dependencies.tokenStore::pairingAccountId,
            clearLocalMediaCache = dependencies.localMediaCacheCleaner::clearAll
        ) as T
    }
}
