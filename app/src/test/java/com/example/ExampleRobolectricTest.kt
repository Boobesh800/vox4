package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CommandType
import com.example.model.AppInfo
import com.example.util.CommandParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoxControl", appName)
    }

    @Test
    fun `test offline voice command parsing`() {
        // Torch
        assertEquals(CommandType.TORCH_ON, CommandParser.parse("turn on torch light").type)
        assertEquals(CommandType.TORCH_OFF, CommandParser.parse("torch off").type)
        assertEquals(CommandType.TORCH_ON, CommandParser.parse("flashlight on").type)

        // Internet & Data
        assertEquals(CommandType.INTERNET_ON, CommandParser.parse("turn on internet").type)
        assertEquals(CommandType.INTERNET_OFF, CommandParser.parse("turn off internet").type)
        assertEquals(CommandType.INTERNET_ON, CommandParser.parse("turn on mobile data").type)

        // Bluetooth
        assertEquals(CommandType.BLUETOOTH_ON, CommandParser.parse("turn on bluetooth").type)
        assertEquals(CommandType.BLUETOOTH_OFF, CommandParser.parse("bluetooth off").type)

        // WiFi
        assertEquals(CommandType.WIFI_ON, CommandParser.parse("turn on wifi").type)
        assertEquals(CommandType.WIFI_OFF, CommandParser.parse("wifi off").type)

        // Open App
        val youtubeCmd = CommandParser.parse("open youtube")
        assertEquals(CommandType.OPEN_APP, youtubeCmd.type)
        assertEquals("com.google.android.youtube", youtubeCmd.targetPackage)

        val whatsappCmd = CommandParser.parse("open whatsapp")
        assertEquals(CommandType.OPEN_APP, whatsappCmd.type)
        assertEquals("com.whatsapp", whatsappCmd.targetPackage)

        // Phone Calls
        val callMomCmd = CommandParser.parse("call mom")
        assertEquals(CommandType.CALL_CONTACT, callMomCmd.type)
        assertEquals("mom", callMomCmd.targetContactOrNumber)

        val callNumCmd = CommandParser.parse("call 9876543210")
        assertEquals(CommandType.CALL_CONTACT, callNumCmd.type)
        assertEquals("9876543210", callNumCmd.targetContactOrNumber)

        val makeCallCmd = CommandParser.parse("make a call to dad")
        assertEquals(CommandType.CALL_CONTACT, makeCallCmd.type)
        assertEquals("dad", makeCallCmd.targetContactOrNumber)

        // Alarms
        val setAlarmAmCmd = CommandParser.parse("set alarm at 7:30 am")
        assertEquals(CommandType.SET_ALARM, setAlarmAmCmd.type)
        assertEquals(7, setAlarmAmCmd.alarmHour)
        assertEquals(30, setAlarmAmCmd.alarmMinute)

        val setAlarmPmCmd = CommandParser.parse("set alarm at 6:15 pm")
        assertEquals(CommandType.SET_ALARM, setAlarmPmCmd.type)
        assertEquals(18, setAlarmPmCmd.alarmHour)
        assertEquals(15, setAlarmPmCmd.alarmMinute)

        val turnOffAlarmWithTime = CommandParser.parse("turn off alarm at 7 am")
        assertEquals(CommandType.DISMISS_ALARM, turnOffAlarmWithTime.type)
        assertEquals(7, turnOffAlarmWithTime.alarmHour)
        assertEquals(0, turnOffAlarmWithTime.alarmMinute)

        val turnOffAlarmGeneral = CommandParser.parse("turn off alarm")
        assertEquals(CommandType.DISMISS_ALARM, turnOffAlarmGeneral.type)
    }

    @Test
    fun `alarm phrases from the speech recognizer`() {
        // Recognizers often write "a.m." / "p.m." or drop the colon
        val pm = CommandParser.parse("Set alarm at 7:30 p.m.")
        assertEquals(CommandType.SET_ALARM, pm.type)
        assertEquals(19, pm.alarmHour)
        assertEquals(30, pm.alarmMinute)

        val am = CommandParser.parse("set alarm for 6 a.m.")
        assertEquals(CommandType.SET_ALARM, am.type)
        assertEquals(6, am.alarmHour)
        assertEquals(0, am.alarmMinute)

        val spaced = CommandParser.parse("set alarm at 7 30 pm")
        assertEquals(CommandType.SET_ALARM, spaced.type)
        assertEquals(19, spaced.alarmHour)
        assertEquals(30, spaced.alarmMinute)

        val relative = CommandParser.parse("set alarm in 10 minutes")
        assertEquals(CommandType.SET_ALARM, relative.type)

        assertEquals(CommandType.DISMISS_ALARM, CommandParser.parse("stop the alarm").type)
        assertEquals(CommandType.DISMISS_ALARM, CommandParser.parse("turn off my alarm").type)
    }

    @Test
    fun `app matching does not pick random apps from partial words`() {
        val apps = listOf(
            AppInfo("com.example.tube", "Tube Map", "tubemap"),
            AppInfo("com.google.android.youtube", "YouTube", "youtube"),
            AppInfo("com.whatsapp", "WhatsApp", "whatsapp")
        )
        assertEquals("com.google.android.youtube", CommandParser.matchApp(apps, "you tube")?.packageName)
        assertEquals("com.whatsapp", CommandParser.matchApp(apps, "whats app")?.packageName)
        // one letter must never launch anything
        assertEquals(null, CommandParser.matchApp(apps, "y"))
        assertEquals(null, CommandParser.matchApp(apps, "zzz"))
    }
}
