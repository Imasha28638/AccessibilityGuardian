package com.sliit.isp.accessibilityguardian.core.monitor

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

data class DeviceIntegrityResult(
    val rooted: Boolean,
    val emulator: Boolean,
    val rootReasons: List<String>,
    val emulatorReasons: List<String>
) {
    val shouldBlock: Boolean
        get() = rooted || emulator

    val detailText: String
        get() = (rootReasons + emulatorReasons).joinToString(separator = "\n")
}

class DeviceIntegrityMonitor(
    private val context: Context
) {

    fun checkIntegrity(): DeviceIntegrityResult {
        val rootReasons = buildList {
            if (hasTestKeys()) {
                add("Device build tags indicate test-keys.")
            }
            if (hasKnownSuBinary()) {
                add("Detected su binary in a known root path.")
            }
            if (hasKnownRootPackages()) {
                add("Detected installed package associated with root management.")
            }
            if (hasWhichSu()) {
                add("Command lookup resolved su on the device.")
            }
        }.distinct()

        val emulatorReasons = detectEmulatorReasons().distinct()

        return DeviceIntegrityResult(
            rooted = rootReasons.isNotEmpty(),
            emulator = emulatorReasons.isNotEmpty(),
            rootReasons = rootReasons,
            emulatorReasons = emulatorReasons
        )
    }

    private fun hasTestKeys(): Boolean {
        return Build.TAGS?.contains("test-keys", ignoreCase = true) == true
    }

    private fun hasKnownSuBinary(): Boolean {
        return COMMON_SU_PATHS.any { path -> File(path).exists() }
    }

    private fun hasKnownRootPackages(): Boolean {
        return ROOT_PACKAGES.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun hasWhichSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            process.inputStream.bufferedReader().use { reader ->
                reader.readLine()?.isNotBlank() == true
            }.also {
                process.destroy()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun detectEmulatorReasons(): List<String> {
        val reasons = mutableListOf<String>()
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()

        if (containsAny(fingerprint, EMULATOR_SIGNATURES) ||
            fingerprint.startsWith("generic", ignoreCase = true) ||
            fingerprint.startsWith("unknown", ignoreCase = true)
        ) {
            reasons.add("Build fingerprint matches an emulator profile.")
        }
        if (containsAny(model, EMULATOR_SIGNATURES)) {
            reasons.add("Device model matches an emulator profile.")
        }
        if (containsAny(manufacturer, EMULATOR_SIGNATURES)) {
            reasons.add("Device manufacturer matches an emulator profile.")
        }
        if (containsAny(brand, EMULATOR_SIGNATURES) || brand.startsWith("generic", ignoreCase = true)) {
            reasons.add("Device brand matches an emulator profile.")
        }
        if (containsAny(device, EMULATOR_SIGNATURES) || device.startsWith("generic", ignoreCase = true)) {
            reasons.add("Device device name matches an emulator profile.")
        }
        if (containsAny(product, EMULATOR_SIGNATURES)) {
            reasons.add("Device product matches an emulator profile.")
        }
        if (containsAny(hardware, EMULATOR_SIGNATURES)) {
            reasons.add("Device hardware matches an emulator profile.")
        }

        return reasons
    }

    private fun containsAny(value: String, candidates: Set<String>): Boolean {
        val normalized = value.lowercase(Locale.US)
        return candidates.any(normalized::contains)
    }

    companion object {
        private val COMMON_SU_PATHS = setOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/su",
            "/system/usr/we-need-root/su",
            "/system/app/Superuser.apk",
            "/cache/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/data/local/tmp/su",
            "/su/bin/su",
            "/apex/com.android.runtime/bin/su"
        )

        private val ROOT_PACKAGES = setOf(
            "com.topjohnwu.magisk",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.koushikdutta.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.chelpus.luckypatcher",
            "com.ramdroid.appquarantine"
        )

        private val EMULATOR_SIGNATURES = setOf(
            "generic",
            "emulator",
            "sdk",
            "simulator",
            "goldfish",
            "ranchu",
            "genymotion",
            "google_sdk",
            "sdk_gphone",
            "vbox",
            "virtualbox",
            "qemu"
        )
    }
}
