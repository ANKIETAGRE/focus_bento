package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object LocalAlarmScheduler {

    fun schedule(context: Context, todo: Todo) {
        if (todo.dueDate == null) return
        if (todo.dueDate <= System.currentTimeMillis()) {
            Log.d("LocalAlarmScheduler", "Skipping schedule: due date is in the past (${todo.dueDate})")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("todo_id", todo.id)
            putExtra("todo_title", todo.title)
            putExtra("todo_description", todo.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        todo.dueDate,
                        pendingIntent
                    )
                    Log.d("LocalAlarmScheduler", "Scheduled exact alarm for task ${todo.id} at ${todo.dueDate}")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        todo.dueDate,
                        pendingIntent
                    )
                    Log.d("LocalAlarmScheduler", "Scheduled inexact alarm fallback for task ${todo.id} at ${todo.dueDate}")
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        todo.dueDate,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        todo.dueDate,
                        pendingIntent
                    )
                }
                Log.d("LocalAlarmScheduler", "Scheduled standard exact alarm for task ${todo.id} at ${todo.dueDate}")
            }
        } catch (e: Exception) {
            Log.e("LocalAlarmScheduler", "Failed to schedule alarm", e)
            // General fallback
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    todo.dueDate,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e("LocalAlarmScheduler", "Secondary fallback failed", e2)
            }
        }
    }

    fun cancel(context: Context, todoId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("LocalAlarmScheduler", "Canceled alarm for task $todoId")
        }
    }
}
