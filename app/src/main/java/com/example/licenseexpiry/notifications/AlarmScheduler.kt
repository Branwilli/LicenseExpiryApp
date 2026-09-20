package com.example.licenseexpiry.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.licenseexpiry.data.LicenseEntry
import java.util.concurrent.TimeUnit

object AlarmScheduler {

    /**
     * Schedules an exact alarm to fire `reminderDaysBefore` days before expiry,
     * at 9am local time on that day.
     */
    fun scheduleReminder(context: Context, entry: LicenseEntry, vehicleNickname: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val reminderTimeMillis = entry.expiryDateMillis -
            TimeUnit.DAYS.toMillis(entry.reminderDaysBefore.toLong())

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_LICENSE_ID, entry.id)
            putExtra(AlarmReceiver.EXTRA_VEHICLE_NAME, vehicleNickname)
            putExtra(AlarmReceiver.EXTRA_LICENSE_TYPE, entry.licenseType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setExactAndAllowWhileIdle ensures it still fires in Doze mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Caller should prompt the user to grant "Alarms & reminders" permission
            // via Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM before calling this.
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderTimeMillis,
            pendingIntent
        )
    }

    fun cancelReminder(context: Context, entryId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
