package com.patrollink.data.ute

import com.patrollink.domain.DeviceType

internal object PatrolDeviceNameClassifier {
    fun typeFor(name: String, scanRecord: ByteArray? = null): DeviceType {
        val normalized = name.uppercase()
        val hex = scanRecord?.joinToString(" ") { "%02X".format(it) }.orEmpty()
        return when {
            isKnownGlassesName(name) || hex.contains("3A 55") -> DeviceType.Glasses
            isKnownAudioName(name) -> DeviceType.Headset
            normalized.startsWith("SMI-") -> DeviceType.Glasses
            "RECORDER" in normalized || ("AI" in normalized && "REC" in normalized) -> DeviceType.Recorder
            else -> DeviceType.Headset
        }
    }

    fun isKnownAudioName(name: String): Boolean {
        val normalized = name.uppercase()
        return "E1-PRO" in normalized ||
            "FORCELINK" in normalized ||
            "HEADSET" in normalized ||
            "耳机" in name
    }

    fun isKnownGlassesName(name: String): Boolean {
        val normalized = name.uppercase()
        return "GLORY GLASS" in normalized ||
            "GLASS" in normalized ||
            "ABA002" in normalized ||
            "眼镜" in name
    }

    fun isKnownUteControlName(name: String): Boolean =
        isKnownAudioName(name) || isKnownGlassesName(name) || name.uppercase().startsWith("SMI-")

    fun hasSimilarAudioName(left: String, right: String): Boolean {
        if (left.isBlank() || right.isBlank()) return false
        val leftNormalized = left.uppercase()
        val rightNormalized = right.uppercase()
        return listOf("E1-PRO", "FORCELINK", "HEADSET", "耳机").any { marker ->
            marker in leftNormalized && marker in rightNormalized
        }
    }
}

internal fun isSystemBluetoothDeviceConnected(
    hiddenConnected: Boolean,
    audioProfileConnected: Boolean,
    name: String
): Boolean {
    return hiddenConnected
}
