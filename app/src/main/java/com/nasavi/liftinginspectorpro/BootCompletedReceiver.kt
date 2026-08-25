package com.nasavi.liftinginspectorpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nasavi.liftinginspectorpro.data.InspectionReminderScheduler

/**
 * אזעקות AlarmManager נמחקות באתחול המכשיר — מתזמן מחדש את כל התזכורות הפעילות.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            InspectionReminderScheduler.rescheduleAll(context)
        }
    }
}
