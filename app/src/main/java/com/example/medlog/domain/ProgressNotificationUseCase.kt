package com.example.medlog.domain

import com.example.medlog.notification.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 封装"今日进度通知"，将 NotificationHelper 与 HomeViewModel 解耦（SRP）。
 * HomeViewModel 只需调用 progressNotif(taken, total, pendingNames)，
 * 不再直接依赖 NotificationHelper。
 */
@Singleton
class ProgressNotificationUseCase @Inject constructor(
    private val notificationHelper: NotificationHelper,
) {
    operator fun invoke(taken: Int, total: Int, pendingNames: List<String>) {
        notificationHelper.showOrUpdateProgressNotification(
            taken        = taken,
            total        = total,
            pendingNames = pendingNames,
        )
    }
}
