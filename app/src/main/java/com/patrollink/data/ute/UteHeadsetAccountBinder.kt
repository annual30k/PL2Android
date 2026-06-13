package com.patrollink.data.ute

import android.util.Log
import com.yc.nadalsdk.bean.HonorAccountConfig
import com.yc.nadalsdk.bean.smart.HeadsetAccountConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class UteHeadsetAccountBinder(
    private val bridge: UteSdkBridge,
    private val pairingAccountIdProvider: () -> String
) {
    suspend fun bind(reason: String): Boolean = withContext(Dispatchers.IO) {
        val accountId = pairingAccountIdProvider().ifBlank { DefaultPairingAccountId }
        if (isKnownGlassesControl()) {
            val honorAccepted = bindHonorAccount(reason, accountId)
            if (honorAccepted) return@withContext true
            if (canUseKnownGlassesWifiWithoutAccountAck(reason)) return@withContext true
        }
        runCatching {
            val firstResponse = bridge.connection.setHeadsetAccount(HeadsetAccountConfig().apply {
                currentHuid = accountId
            })
            val response = if (firstResponse.data?.accountJudgmentStatus == HeadsetAccountConfig.ACCOUNT_DIFFERENT) {
                delay(AccountRebindConfirmWaitMillis)
                bridge.connection.setHeadsetAccount(HeadsetAccountConfig().apply {
                    currentHuid = accountId
                })
            } else {
                firstResponse
            }
            val accepted = response.isSuccess && response.data.isAccepted()
            Log.i(
                Tag,
                "$reason setHeadsetAccount success=${response.isSuccess},accepted=$accepted,error=${response.errorCode},status=${response.data?.accountJudgmentStatus},account=${accountId.take(8)}..."
            )
            accepted
        }.getOrElse { throwable ->
            Log.w(Tag, "$reason setHeadsetAccount failed: ${throwable.message}", throwable)
            false
        }
    }

    private fun bindHonorAccount(reason: String, accountId: String): Boolean =
        runCatching {
            val response = bridge.connection.setHonorAccount(HonorAccountConfig().apply {
                currentHuid = accountId
            })
            val accepted = response.isSuccess && response.data.isAccepted()
            Log.i(
                Tag,
                "$reason setHonorAccount success=${response.isSuccess},accepted=$accepted,error=${response.errorCode},status=${response.data?.accountJudgmentStatus},account=${accountId.take(8)}..."
            )
            accepted
        }.getOrElse { throwable ->
            Log.w(Tag, "$reason setHonorAccount failed: ${throwable.message}", throwable)
            false
        }

    private fun canUseKnownGlassesWifiWithoutAccountAck(reason: String): Boolean {
        val deviceName = bridge.client.deviceName.orEmpty()
        val glassesInfo = runCatching { bridge.connection.getGlassesInfo().takeIf { it.isSuccess }?.data }.getOrNull()
        val wifiInfo = runCatching { bridge.connection.smartGetDeviceWiFiInfo().takeIf { it.isSuccess }?.data }.getOrNull()
        val accepted = UteWifiAccountAcceptance.canUseKnownGlassesWifiWithoutAccountAck(
            deviceName = deviceName,
            hasGlassesStore = glassesInfo?.glassesStoreInfo != null,
            ssid = wifiInfo?.wiFiSSID.orEmpty(),
            password = wifiInfo?.wiFiPassword.orEmpty()
        )
        Log.i(
            Tag,
            "$reason knownGlassesWifiFallback accepted=$accepted,device=$deviceName,hasStore=${glassesInfo?.glassesStoreInfo != null},ssid=${wifiInfo?.wiFiSSID.orEmpty()},passwordConfigured=${!wifiInfo?.wiFiPassword.isNullOrBlank()}"
        )
        return accepted
    }

    private fun isKnownGlassesControl(): Boolean =
        PatrolDeviceNameClassifier.isKnownGlassesName(bridge.client.deviceName.orEmpty())

    private fun HonorAccountConfig?.isAccepted(): Boolean =
        this == null ||
            accountJudgmentStatus == HonorAccountConfig.ACCOUNT_SAME ||
            accountJudgmentStatus == HonorAccountConfig.ACCOUNT_NO

    private fun HeadsetAccountConfig?.isAccepted(): Boolean =
        this == null ||
            accountJudgmentStatus == HeadsetAccountConfig.ACCOUNT_SAME ||
            accountJudgmentStatus == HeadsetAccountConfig.ACCOUNT_NO

    private companion object {
        const val Tag = "UteHeadsetAccount"
        const val DefaultPairingAccountId = "patrollink-local-operator"
        const val AccountRebindConfirmWaitMillis = 900L
    }
}

internal object UteAccountBindingGuard {
    fun requireAcceptedForWifi(accepted: Boolean) {
        check(accepted) { AccountMismatchMessage }
    }

    const val AccountMismatchMessage = "设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink"
}

internal object UteWifiAccountAcceptance {
    fun canUseKnownGlassesWifiWithoutAccountAck(
        deviceName: String,
        hasGlassesStore: Boolean,
        ssid: String,
        password: String
    ): Boolean =
        PatrolDeviceNameClassifier.isKnownGlassesName(deviceName) &&
            hasGlassesStore &&
            ssid.isNotBlank() &&
            password.isNotBlank()
}
