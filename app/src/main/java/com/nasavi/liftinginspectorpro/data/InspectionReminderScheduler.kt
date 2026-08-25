package com.nasavi.liftinginspectorpro.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nasavi.liftinginspectorpro.InspectionReminderReceiver
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * מתזמן תזכורות מקומיות (AlarmManager, ללא אינטרנט) לבדיקה הבאה של תסקיר.
 * בדיקות 1-4 בשרשרת: תזכורת שבוע מראש. בדיקה 5 (נדרש בודק מוסמך חיצוני): תזכורת חודש מראש.
 */
object InspectionReminderScheduler {

    private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun parseDateOrNull(text: String): LocalDate? =
        try { LocalDate.parse(text.trim(), DATE_FMT) } catch (_: Exception) { null }

    private fun pendingIntent(context: Context, reportNumber: String, message: String): PendingIntent {
        val intent = Intent(context, InspectionReminderReceiver::class.java).apply {
            putExtra("reportNumber", reportNumber)
            putExtra("message", message)
        }
        return PendingIntent.getBroadcast(
            context,
            reportNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelReminder(context: Context, reportNumber: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, InspectionReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            reportNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    fun scheduleReminder(context: Context, report: ReportStorage.WorkingReport) {
        cancelReminder(context, report.reportNumber)

        val nextDate = parseDateOrNull(report.nextInspectionDate) ?: return
        val daysBefore = if (report.chainIndex >= 5) 30L else 7L
        val reminderDate = nextDate.minusDays(daysBefore)
        if (reminderDate.isBefore(LocalDate.now())) return

        val triggerMillis = reminderDate
            .atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val message = if (report.chainIndex >= 5) {
            "הבדיקה הבאה — נדרש בודק מוסמך חיצוני בעוד חודש"
        } else {
            "תסקיר ${report.reportNumber} — הבדיקה הבאה בעוד שבוע"
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent(context, report.reportNumber, message)
        )
    }

    fun rescheduleAll(context: Context) {
        ReportStorage.loadWorkingReports(context).forEach { report ->
            scheduleReminder(context, report)
        }
    }
}
