package com.hackathon.apprisk.domain

import com.hackathon.apprisk.domain.model.PermissionCategory

data class PermissionDefinition(
    val category: PermissionCategory,
    val title: String,
    val explanation: String,
    val baseWeight: Int,
)

object PermissionCatalog {
    private fun item(
        category: PermissionCategory,
        title: String,
        explanation: String,
        weight: Int = 0,
    ) = PermissionDefinition(category, title, explanation, weight)

    private val definitions = buildMap {
        fun add(name: String, category: PermissionCategory, title: String, explanation: String, weight: Int = 0) {
            put(name, item(category, title, explanation, weight))
        }

        add("android.permission.SYSTEM_ALERT_WINDOW", PermissionCategory.SPECIAL, "Display over other apps", "Can draw windows on top of other apps.", RiskWeights.OVERLAY_GRANTED)
        add("android.permission.REQUEST_INSTALL_PACKAGES", PermissionCategory.SPECIAL, "Install other apps", "Can ask Android to install apps from outside the Play Store.", RiskWeights.INSTALL_PACKAGES_GRANTED)
        add("android.permission.PACKAGE_USAGE_STATS", PermissionCategory.SPECIAL, "View app usage", "Can see which apps you use and how often.", RiskWeights.USAGE_STATS_GRANTED)
        add("android.permission.MANAGE_EXTERNAL_STORAGE", PermissionCategory.STORAGE, "Manage all files", "Can read and change most files on shared storage.", RiskWeights.MANAGE_EXTERNAL_STORAGE)
        add("android.permission.WRITE_SETTINGS", PermissionCategory.SPECIAL, "Change system settings", "Can change some device settings.", 0)
        add("android.permission.BIND_ACCESSIBILITY_SERVICE", PermissionCategory.SPECIAL, "Accessibility service", "Can observe screen content and interact on your behalf.", RiskWeights.ACCESSIBILITY_ENABLED)
        add("android.permission.BIND_DEVICE_ADMIN", PermissionCategory.SPECIAL, "Device administrator", "Can enforce device policies and may prevent removal until disabled.", RiskWeights.DEVICE_ADMIN_ACTIVE)
        add("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", PermissionCategory.SPECIAL, "Notification access", "Can read notifications, which may contain message previews and codes.", RiskWeights.NOTIFICATION_LISTENER_ENABLED)
        add("android.permission.BIND_VPN_SERVICE", PermissionCategory.SPECIAL, "VPN service", "Can route device network traffic through a VPN.", 0)
        add("android.permission.SCHEDULE_EXACT_ALARM", PermissionCategory.BACKGROUND, "Schedule exact alarms", "Can wake the device at precise times.", 0)
        add("android.permission.USE_FULL_SCREEN_INTENT", PermissionCategory.SPECIAL, "Full-screen alerts", "Can show urgent full-screen notifications.", 0)

        add("android.permission.READ_SMS", PermissionCategory.SMS, "Read text messages", "Can read text messages, including one-time passwords.", RiskWeights.READ_SMS)
        add("android.permission.RECEIVE_SMS", PermissionCategory.SMS, "Receive text messages", "Can receive and process incoming text messages.", RiskWeights.RECEIVE_SMS)
        add("android.permission.SEND_SMS", PermissionCategory.SMS, "Send text messages", "Can send text messages, possibly creating charges.", RiskWeights.SEND_SMS)
        add("android.permission.RECEIVE_MMS", PermissionCategory.SMS, "Receive picture messages", "Can receive multimedia messages.", 0)
        add("android.permission.RECEIVE_WAP_PUSH", PermissionCategory.SMS, "Receive WAP messages", "Can receive WAP push messages.", 0)
        add("android.permission.READ_CONTACTS", PermissionCategory.CONTACTS, "Read contacts", "Can read names, phone numbers and email addresses.", RiskWeights.READ_CONTACTS)
        add("android.permission.WRITE_CONTACTS", PermissionCategory.CONTACTS, "Change contacts", "Can add or change contacts on your device.", RiskWeights.WRITE_CONTACTS_OR_CALL_LOG)
        add("android.permission.GET_ACCOUNTS", PermissionCategory.CONTACTS, "Find device accounts", "Can see some account names on the device.", 0)
        add("android.permission.READ_CALL_LOG", PermissionCategory.CONTACTS, "Read call history", "Can see incoming and outgoing call records.", RiskWeights.READ_CALL_LOG)
        add("android.permission.WRITE_CALL_LOG", PermissionCategory.CONTACTS, "Change call history", "Can add or change call records.", RiskWeights.WRITE_CONTACTS_OR_CALL_LOG)
        add("android.permission.PROCESS_OUTGOING_CALLS", PermissionCategory.CONTACTS, "Observe outgoing calls", "Can observe outgoing call activity.", 0)
        add("android.permission.CAMERA", PermissionCategory.CAMERA_MIC, "Use the camera", "Can take photos or record video when the camera is available.", RiskWeights.CAMERA)
        add("android.permission.RECORD_AUDIO", PermissionCategory.CAMERA_MIC, "Record audio", "Can record sound using the microphone.", RiskWeights.RECORD_AUDIO)
        add("android.permission.ACCESS_FINE_LOCATION", PermissionCategory.LOCATION, "Precise location", "Can determine your location with high accuracy.", RiskWeights.ACCESS_FINE_LOCATION)
        add("android.permission.ACCESS_COARSE_LOCATION", PermissionCategory.LOCATION, "Approximate location", "Can estimate your general location.", RiskWeights.ACCESS_COARSE_LOCATION)
        add("android.permission.ACCESS_BACKGROUND_LOCATION", PermissionCategory.LOCATION, "Location in background", "Can access location when you are not using the app.", RiskWeights.ACCESS_BACKGROUND_LOCATION)
        add("android.permission.READ_PHONE_STATE", PermissionCategory.PHONE, "Read phone state", "Can access some phone and network identifiers.", RiskWeights.READ_PHONE_STATE)
        add("android.permission.READ_PHONE_NUMBERS", PermissionCategory.PHONE, "Read phone number", "Can access the phone number associated with the device.", RiskWeights.READ_PHONE_NUMBERS)
        add("android.permission.CALL_PHONE", PermissionCategory.PHONE, "Place calls", "Can initiate calls without opening the dialer.", RiskWeights.CALL_PHONE)
        add("android.permission.ANSWER_PHONE_CALLS", PermissionCategory.PHONE, "Answer calls", "Can answer incoming phone calls.", 0)
        add("android.permission.READ_PRECISE_PHONE_STATE", PermissionCategory.PHONE, "Read precise phone state", "Can read additional phone network state.", 0)
        add("android.permission.ADD_VOICEMAIL", PermissionCategory.PHONE, "Add voicemail", "Can add voicemail entries.", 0)
        add("android.permission.READ_EXTERNAL_STORAGE", PermissionCategory.STORAGE, "Read shared files", "Can read files available in shared storage.", RiskWeights.READ_MEDIA_OR_SHARED_STORAGE)
        add("android.permission.WRITE_EXTERNAL_STORAGE", PermissionCategory.STORAGE, "Change shared files", "Can create or change files in shared storage.", 0)
        add("android.permission.READ_MEDIA_IMAGES", PermissionCategory.STORAGE, "Read photos", "Can access images you keep on this device.", RiskWeights.READ_MEDIA_OR_SHARED_STORAGE)
        add("android.permission.READ_MEDIA_VIDEO", PermissionCategory.STORAGE, "Read videos", "Can access video files you keep on this device.", RiskWeights.READ_MEDIA_OR_SHARED_STORAGE)
        add("android.permission.READ_MEDIA_AUDIO", PermissionCategory.STORAGE, "Read audio", "Can access audio files you keep on this device.", RiskWeights.READ_MEDIA_OR_SHARED_STORAGE)
        add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED", PermissionCategory.STORAGE, "Read selected photos", "Can access photos or videos you select.", RiskWeights.READ_MEDIA_OR_SHARED_STORAGE)
        add("android.permission.RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND, "Start after reboot", "Can run after the device restarts.", RiskWeights.RECEIVE_BOOT_COMPLETED)
        add("android.permission.FOREGROUND_SERVICE", PermissionCategory.BACKGROUND, "Run an ongoing task", "Can run a visible ongoing task in the background.", RiskWeights.FOREGROUND_SERVICE)
        add("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", PermissionCategory.BACKGROUND, "Ignore battery limits", "Can request fewer battery restrictions.", RiskWeights.IGNORE_BATTERY_OPTIMIZATIONS)
        add("android.permission.WAKE_LOCK", PermissionCategory.BACKGROUND, "Keep device awake", "Can keep the device from sleeping during work.", 0)
        add("android.permission.USE_EXACT_ALARM", PermissionCategory.BACKGROUND, "Schedule exact alarms", "Can schedule exact alarms for supported use cases.", 0)
        add("android.permission.INTERNET", PermissionCategory.NETWORK, "Connect to the internet", "Can send and receive data over the internet.", RiskWeights.INTERNET)
        add("android.permission.ACCESS_NETWORK_STATE", PermissionCategory.NETWORK, "Check network status", "Can see whether a network connection is available.", 0)
        add("android.permission.ACCESS_WIFI_STATE", PermissionCategory.NETWORK, "Check Wi-Fi status", "Can see information about the Wi-Fi connection.", 0)
        add("android.permission.CHANGE_WIFI_STATE", PermissionCategory.NETWORK, "Change Wi-Fi settings", "Can change some Wi-Fi connection settings.", 0)
        add("android.permission.CHANGE_NETWORK_STATE", PermissionCategory.NETWORK, "Change network state", "Can change some network connectivity settings.", 0)
    }

    private val specialPermissionNames = setOf(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.WRITE_SETTINGS",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_VPN_SERVICE",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.USE_FULL_SCREEN_INTENT",
    )

    fun definition(name: String): PermissionDefinition? =
        definitions[name] ?: if (name.startsWith("android.permission.FOREGROUND_SERVICE_")) {
            item(
                PermissionCategory.BACKGROUND,
                "Foreground service",
                "Can run a visible ongoing task in the background.",
                RiskWeights.FOREGROUND_SERVICE,
            )
        } else null

    fun category(name: String): PermissionCategory =
        definition(name)?.category ?: PermissionCategory.OTHER

    fun title(name: String): String =
        definition(name)?.title ?: name.substringAfterLast('.').replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    fun explanation(name: String): String =
        definition(name)?.explanation ?: "This permission allows an app to access an Android feature."

    fun baseWeight(name: String): Int = definition(name)?.baseWeight ?: 0

    fun isKnown(name: String): Boolean = definition(name) != null

    fun isSpecial(name: String): Boolean =
        name in specialPermissionNames || name.startsWith("android.permission.BIND_") &&
            (name.contains("ACCESSIBILITY") || name.contains("DEVICE_ADMIN") || name.contains("NOTIFICATION_LISTENER"))

    fun isSensitive(name: String): Boolean =
        category(name) in setOf(
            PermissionCategory.SPECIAL,
            PermissionCategory.SMS,
            PermissionCategory.CONTACTS,
            PermissionCategory.CAMERA_MIC,
            PermissionCategory.LOCATION,
            PermissionCategory.PHONE,
            PermissionCategory.STORAGE,
            PermissionCategory.BACKGROUND,
        ) && (name != "android.permission.FOREGROUND_SERVICE" || baseWeight(name) > 0)

    fun allDefinitions(): Map<String, PermissionDefinition> = definitions

    fun categoryOrder(): List<PermissionCategory> = listOf(
        PermissionCategory.SPECIAL,
        PermissionCategory.SMS,
        PermissionCategory.CONTACTS,
        PermissionCategory.CAMERA_MIC,
        PermissionCategory.LOCATION,
        PermissionCategory.PHONE,
        PermissionCategory.STORAGE,
        PermissionCategory.BACKGROUND,
        PermissionCategory.NETWORK,
    )
}
