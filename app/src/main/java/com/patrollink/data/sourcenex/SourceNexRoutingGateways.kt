package com.patrollink.data.sourcenex

import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceFactoryResetTarget
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.TransferTarget
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow

class RoutingDeviceGateway(
    private val ute: DeviceGateway,
    private val sourceNex: DeviceGateway
) : DeviceGateway {
    override fun scan(): Flow<List<ScannedDevice>> = combine(ute.scan(), sourceNex.scan()) { a, b ->
        (a.filterNot { SourceNexDeviceGateway.isSourceNex(it.name) } + b).distinctBy { it.id }
    }
    override suspend fun bind(deviceId: String) = route(deviceId).bind(deviceId)
    override suspend fun unbind(deviceId: String) = route(deviceId).unbind(deviceId)
    override suspend fun sendCommand(deviceId: String, command: DeviceCommand) = route(deviceId).sendCommand(deviceId, command)
    private fun route(id: String) = if (id.startsWith(SourceNexDeviceGateway.IdPrefix)) sourceNex else ute
}

class RoutingMediaGateway(
    private val bridge: SourceNexBridge,
    private val ute: MediaGateway,
    private val sourceNex: MediaGateway
) : MediaGateway {
    private fun active() = if (bridge.isActive()) sourceNex else ute
    override suspend fun listFiles(local: Boolean) = if (local) {
        val sourceFiles = runCatching { sourceNex.listFiles(true) }.getOrDefault(emptyList())
        val uteFiles = runCatching { ute.listFiles(true) }.getOrDefault(emptyList())
        (sourceFiles + uteFiles).distinctBy { it.id }
    } else active().listFiles(false)
    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> =
        if (fileId.startsWith(SourceNexMediaGateway.IdPrefix)) sourceNex.transfer(fileId, target) else ute.transfer(fileId, target)
    override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String) = active().uploadLocalFile(file, storageSide, bizType, bizId)
    override suspend fun delete(fileId: String, local: Boolean) =
        if (fileId.startsWith(SourceNexMediaGateway.IdPrefix)) sourceNex.delete(fileId, local) else ute.delete(fileId, local)
    override suspend fun verifySha256(fileId: String) =
        if (fileId.startsWith(SourceNexMediaGateway.IdPrefix)) sourceNex.verifySha256(fileId) else ute.verifySha256(fileId)
}

class RoutingDeviceControlGateway(
    private val bridge: SourceNexBridge,
    private val ute: DeviceControlGateway
) : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> = if (bridge.isActive()) emptyFlow() else ute.events()
    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = if (device.id.startsWith(SourceNexDeviceGateway.IdPrefix) || bridge.isActive()) {
        DeviceCapabilities(supportsGlasses = true, supportsWifi = false, supportsFileTransfer = true, supportsPhoto = true,
            supportsVideo = true, supportsAudioRecord = true, supportsRealtimeAudio = false)
    } else ute.capabilities(device)
    override suspend fun readWifi(): DeviceWifiState = if (bridge.isActive()) DeviceWifiState(enabled = false) else ute.readWifi()
    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState =
        if (bridge.isActive()) DeviceWifiState(enabled = false) else ute.configureWifi(enabled, ssid, password)
    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings) = if (bridge.isActive()) settings else ute.applySettings(device, settings)
    override suspend fun startRealtimeAudioSync(sessionId: String) = false
    override suspend fun stopRealtimeAudioSync() = false
    override suspend fun notifyMediaSyncCompleted() = if (bridge.isActive()) true else ute.notifyMediaSyncCompleted()
    override suspend fun clearDeviceAccount() = if (bridge.isActive()) false else ute.clearDeviceAccount()
    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget) = if (bridge.isActive()) false else ute.factoryResetDevice(target)
}
