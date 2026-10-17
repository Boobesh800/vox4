package com.example.util

import com.example.model.AppInfo
import com.example.model.CommandType
import com.example.model.ParsedCommand
import java.util.Calendar
import java.util.Locale

object CommandParser {

    // Common well-known package aliases for instant zero-lag lookup
    private val KNOWN_APP_ALIASES = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatspp" to "com.whatsapp",
        "whatsapp" to "com.whatsapp",
        "what app" to "com.whatsapp",
        "whats app" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "google" to "com.google.android.googlequicksearchbox",
        "camera" to "com.android.camera",
        "calculator" to "com.google.android.calculator",
        "settings" to "com.android.settings",
        "spotify" to "com.spotify.music",
        "maps" to "com.google.android.apps.maps",
        "gallery" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "clock" to "com.google.android.deskclock",
        "alarm" to "com.google.android.deskclock",
        "gmail" to "com.google.android.gm",
        "mail" to "com.google.android.gm",
        "messages" to "com.google.android.apps.messaging",
        "message" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "contacts" to "com.google.android.contacts",
        "dialer" to "com.google.android.dialer",
        "phone" to "com.google.android.dialer",
        "calendar" to "com.google.android.calendar",
        "files" to "com.google.android.apps.nbu.files",
        "play store" to "com.android.vending",
        "store" to "com.android.vending"
    )

    fun parse(rawText: String, installedApps: List<AppInfo> = emptyList()): ParsedCommand {
        val normalized = normalize(rawText)

        // 1. Alarm Commands ("set alarm at 7:30 am", "turn off alarm at 7 am", "turn off alarm")
        val alarmCommand = checkAlarm(normalized, rawText)
        if (alarmCommand != null) {
            return alarmCommand
        }

        // 2. Phone Call Commands (e.g., "call mom", "call dad", "call 9876543210", "make a call to...")
        val callCommand = checkCall(normalized, rawText)
        if (callCommand != null) {
            return callCommand
        }

        // 3. Torch / Flashlight Commands
        if (isTorchOn(normalized)) {
            return ParsedCommand(CommandType.TORCH_ON, rawText)
        }
        if (isTorchOff(normalized)) {
            return ParsedCommand(CommandType.TORCH_OFF, rawText)
        }

        // 4. Internet / Mobile Data Commands
        if (isInternetOn(normalized)) {
            return ParsedCommand(CommandType.INTERNET_ON, rawText)
        }
        if (isInternetOff(normalized)) {
            return ParsedCommand(CommandType.INTERNET_OFF, rawText)
        }

        // 5. Wi-Fi Commands
        if (isWifiOn(normalized)) {
            return ParsedCommand(CommandType.WIFI_ON, rawText)
        }
        if (isWifiOff(normalized)) {
            return ParsedCommand(CommandType.WIFI_OFF, rawText)
        }

        // 6. Bluetooth Commands
        if (isBluetoothOn(normalized)) {
            return ParsedCommand(CommandType.BLUETOOTH_ON, rawText)
        }
        if (isBluetoothOff(normalized)) {
            return ParsedCommand(CommandType.BLUETOOTH_OFF, rawText)
        }

        // 7. Open App Commands (e.g., "open youtube", "open whatspp", "open whatsapp", "open camera")
        val openAppCommand = checkOpenApp(normalized, rawText, installedApps)
        if (openAppCommand != null) {
            return openAppCommand
        }

        // 8. Volume Commands
        if (normalized.contains("volume up") || normalized.contains("increase volume") || normalized.contains("louder")) {
            return ParsedCommand(CommandType.VOLUME_UP, rawText)
        }
        if (normalized.contains("volume down") || normalized.contains("decrease volume") || normalized.contains("lower volume") || normalized.contains("softer")) {
            return ParsedCommand(CommandType.VOLUME_DOWN, rawText)
        }
        if (normalized.contains("mute") || normalized.contains("silent") || normalized.contains("silence")) {
            return ParsedCommand(CommandType.VOLUME_MUTE, rawText)
        }

        // 9. Battery Check
        if (normalized.contains("battery") || normalized.contains("battery percentage") || normalized.contains("battery level")) {
            return ParsedCommand(CommandType.BATTERY_CHECK, rawText)
        }

        // 10. Time Check
        if (normalized.contains("what time") || normalized.contains("tell time") || normalized.contains("current time") || normalized.contains("what is the time")) {
            return ParsedCommand(CommandType.TIME_CHECK, rawText)
        }

        return ParsedCommand(CommandType.UNKNOWN, rawText)
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            // "7:30 a.m." / "7 p.m" / "7:30am"  ->  "7:30 am" / "7 pm" / "7:30 am"
            .replace(Regex("""(\d)\s*([ap])\.?\s?m\.?(?=\s|$)"""), "$1 $2m")
            .replace(Regex("[^a-z0-9\\s:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun checkAlarm(normalized: String, rawText: String): ParsedCommand? {
        val isTurnOff = normalized.contains("turn off alarm") ||
                normalized.contains("turn off alram") ||
                normalized.contains("cancel alarm") ||
                normalized.contains("stop alarm") ||
                normalized.contains("dismiss alarm") ||
                normalized.contains("disable alarm") ||
                normalized.contains("off alarm") ||
                normalized.contains("alarm off") ||
                normalized.contains("alarm stop") ||
                Regex("""(turn off|switch off|cancel|stop|dismiss|disable|delete|remove)\s+(the |my |this )?(alarm|alram)""").containsMatchIn(normalized)

        if (isTurnOff) {
            val time = extractTime(normalized)
            val displayTime = if (time != null) formatTimeDisplay(time.first, time.second) else null
            return ParsedCommand(
                type = CommandType.DISMISS_ALARM,
                rawText = rawText,
                alarmHour = time?.first,
                alarmMinute = time?.second,
                alarmTimeDisplay = displayTime
            )
        }

        val isSetAlarm = normalized.contains("set alarm") ||
                normalized.contains("set alram") ||
                normalized.contains("set an alarm") ||
                normalized.contains("set a alarm") ||
                normalized.contains("wake me up") ||
                normalized.startsWith("alarm ") ||
                normalized.startsWith("alarm at ") ||
                normalized.startsWith("alarm for ")

        if (isSetAlarm) {
            val time = extractTime(normalized) ?: Pair(7, 0) // default 7:00 AM if no time mentioned
            val displayTime = formatTimeDisplay(time.first, time.second)
            return ParsedCommand(
                type = CommandType.SET_ALARM,
                rawText = rawText,
                alarmHour = time.first,
                alarmMinute = time.second,
                alarmTimeDisplay = displayTime
            )
        }

        return null
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        // 0. Relative: "in 10 minutes", "after 2 hours"
        val regexRelative = Regex("""(?:in|after)\s+(\d{1,3})\s*(hours?|hrs?|minutes?|mins?)\b""")
        val matchRelative = regexRelative.find(text)
        if (matchRelative != null) {
            val amount = matchRelative.groupValues[1].toInt()
            val isHours = matchRelative.groupValues[2].startsWith("h")
            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, if (isHours) amount * 60 else amount)
            return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        // 1. Check "7:30 am" or "19:30"
        val regexColon = Regex("""(\d{1,2}):(\d{2})\s*(am|pm)?""")
        val matchColon = regexColon.find(text)
        if (matchColon != null) {
            var hour = matchColon.groupValues[1].toInt()
            val min = matchColon.groupValues[2].toInt()
            val ampm = matchColon.groupValues[3].lowercase(Locale.ROOT)
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23 && min in 0..59) return Pair(hour, min)
        }

        // 2. Check "7 am" or "7 pm"
        val regexHourAmpm = Regex("""(\d{1,2})\s*(am|pm)""")
        val matchHourAmpm = regexHourAmpm.find(text)
        if (matchHourAmpm != null) {
            var hour = matchHourAmpm.groupValues[1].toInt()
            val ampm = matchHourAmpm.groupValues[2].lowercase(Locale.ROOT)
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23) return Pair(hour, 0)
        }

        // 2b. "7 30 am" (recognizer dropped the colon)
        val regexSpaced = Regex("""(\d{1,2})\s+(\d{2})\s*(am|pm)""")
        val matchSpaced = regexSpaced.find(text)
        if (matchSpaced != null) {
            var hour = matchSpaced.groupValues[1].toInt()
            val min = matchSpaced.groupValues[2].toInt()
            val ampm = matchSpaced.groupValues[3]
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23 && min in 0..59) return Pair(hour, min)
        }

        // 3. Check "at 7" or "for 7"
        val regexSimple = Regex("""(?:at|for)\s+(\d{1,2})(?:\s+o clock)?""")
        val matchSimple = regexSimple.find(text)
        if (matchSimple != null) {
            val hour = matchSimple.groupValues[1].toInt()
            if (hour in 1..24) {
                val adjustedHour = if (hour == 24) 0 else hour
                return Pair(adjustedHour, 0)
            }
        }

        return null
    }

    private fun formatTimeDisplay(hour: Int, minute: Int): String {
        val ampm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val minStr = if (minute < 10) "0$minute" else "$minute"
        return "$displayHour:$minStr $ampm"
    }

    private fun checkCall(normalized: String, rawText: String): ParsedCommand? {
        val callPrefixes = listOf(
            "make a call to ",
            "make call to ",
            "call to ",
            "place a call to ",
            "dial ",
            "call "
        )

        for (prefix in callPrefixes) {
            val idx = normalized.indexOf(prefix)
            if (idx >= 0) {
                var target = normalized.substring(idx + prefix.length).trim()
                target = target.replace(Regex("^(my|the)\\s+"), "").trim()
                if (target.isNotEmpty()) {
                    return ParsedCommand(
                        type = CommandType.CALL_CONTACT,
                        rawText = rawText,
                        targetContactOrNumber = target
                    )
                }
            }
        }
        return null
    }

    private fun isTorchOn(text: String): Boolean {
        val matchesTurnOn = (text.contains("turn on") || text.contains("switch on") || text.contains("enable") || text.contains("start")) &&
                (text.contains("torch") || text.contains("flashlight") || text.contains("light") || text.contains("flash"))

        val matchesSuffix = (text.contains("torch on") || text.contains("flashlight on") || text.contains("light on") || text.contains("torch light on"))

        return (matchesTurnOn || matchesSuffix) && !text.contains("off")
    }

    private fun isTorchOff(text: String): Boolean {
        val matchesTurnOff = (text.contains("turn off") || text.contains("switch off") || text.contains("disable") || text.contains("stop")) &&
                (text.contains("torch") || text.contains("flashlight") || text.contains("light") || text.contains("flash"))

        val matchesSuffix = (text.contains("torch off") || text.contains("flashlight off") || text.contains("light off") || text.contains("torch light off"))

        return matchesTurnOff || matchesSuffix
    }

    private fun isInternetOn(text: String): Boolean {
        val matchesOn = (text.contains("turn on") || text.contains("switch on") || text.contains("enable")) &&
                (text.contains("internet") || text.contains("mobile data") || text.contains("cellular data") || text.contains("data connection") || text.contains("net on"))

        val matchesSuffix = text.contains("internet on") || text.contains("mobile data on") || text.contains("data on")
        return (matchesOn || matchesSuffix) && !text.contains("off")
    }

    private fun isInternetOff(text: String): Boolean {
        val matchesOff = (text.contains("turn off") || text.contains("switch off") || text.contains("disable")) &&
                (text.contains("internet") || text.contains("mobile data") || text.contains("cellular data") || text.contains("data connection") || text.contains("net off"))

        val matchesSuffix = text.contains("internet off") || text.contains("mobile data off") || text.contains("data off")
        return matchesOff || matchesSuffix
    }

    private fun isWifiOn(text: String): Boolean {
        val matchesOn = (text.contains("turn on") || text.contains("switch on") || text.contains("enable")) &&
                (text.contains("wifi") || text.contains("wi fi"))

        val matchesSuffix = text.contains("wifi on") || text.contains("wi fi on")
        return (matchesOn || matchesSuffix) && !text.contains("off")
    }

    private fun isWifiOff(text: String): Boolean {
        val matchesOff = (text.contains("turn off") || text.contains("switch off") || text.contains("disable")) &&
                (text.contains("wifi") || text.contains("wi fi"))

        val matchesSuffix = text.contains("wifi off") || text.contains("wi fi off")
        return matchesOff || matchesSuffix
    }

    private fun isBluetoothOn(text: String): Boolean {
        val matchesOn = (text.contains("turn on") || text.contains("switch on") || text.contains("enable")) &&
                text.contains("bluetooth")

        val matchesSuffix = text.contains("bluetooth on")
        return (matchesOn || matchesSuffix) && !text.contains("off")
    }

    private fun isBluetoothOff(text: String): Boolean {
        val matchesOff = (text.contains("turn off") || text.contains("switch off") || text.contains("disable")) &&
                text.contains("bluetooth")

        val matchesSuffix = text.contains("bluetooth off")
        return matchesOff || matchesSuffix
    }

    /**
     * Finds the installed app the user meant. Exact label first, then prefix, then "contains".
     * Short or partial queries never match by accident (the old matcher opened random apps).
     */
    fun matchApp(installedApps: List<AppInfo>, query: String): AppInfo? {
        val q = query.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        if (q.length < 2 || installedApps.isEmpty()) return null
        return installedApps.firstOrNull { it.normalizedLabel == q }
            ?: installedApps.firstOrNull { it.normalizedLabel.startsWith(q) }
            ?: installedApps.firstOrNull { q.length >= 3 && it.normalizedLabel.contains(q) }
            ?: installedApps.firstOrNull { it.normalizedLabel.length >= 4 && q.contains(it.normalizedLabel) }
    }

    private fun checkOpenApp(normalized: String, rawText: String, installedApps: List<AppInfo>): ParsedCommand? {
        val openPrefixes = listOf("open ", "launch ", "start ", "go to ", "run ")
        var targetAppName: String? = null

        for (prefix in openPrefixes) {
            val idx = normalized.indexOf(prefix)
            if (idx >= 0) {
                targetAppName = normalized.substring(idx + prefix.length).trim()
                break
            }
        }

        if (targetAppName.isNullOrBlank()) {
            if (KNOWN_APP_ALIASES.containsKey(normalized)) {
                targetAppName = normalized
            }
        }

        if (targetAppName.isNullOrBlank()) {
            return null
        }

        val cleanAppName = targetAppName
            .replace(Regex("^(the|my)\\s+"), "")
            .replace(Regex("\\s+(app|application)$"), "")
            .trim()

        if (cleanAppName.isEmpty()) return null

        val knownPackage = KNOWN_APP_ALIASES[cleanAppName]
        var bestMatchPackage: String? = knownPackage
        var bestMatchLabel: String = cleanAppName

        val match = matchApp(installedApps, cleanAppName)
        if (match != null) {
            bestMatchPackage = match.packageName
            bestMatchLabel = match.label
        }

        return ParsedCommand(
            type = CommandType.OPEN_APP,
            rawText = rawText,
            targetApp = bestMatchLabel,
            targetPackage = bestMatchPackage
        )
    }
}
