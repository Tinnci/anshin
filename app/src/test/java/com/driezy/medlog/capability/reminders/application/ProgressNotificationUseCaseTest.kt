package com.driezy.medlog.capability.reminders.application

import com.driezy.medlog.capability.reminders.NotificationHelper
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ProgressNotificationUseCaseTest {

    private val notificationHelper: NotificationHelper = mock()
    private val useCase = ProgressNotificationUseCase(notificationHelper)

    @Test
    fun `invoke updates today progress notification`() {
        useCase(taken = 2, total = 4, pendingNames = listOf("Amoxicillin"))

        verify(notificationHelper).showOrUpdateProgressNotification(
            taken = 2,
            total = 4,
            pendingNames = listOf("Amoxicillin"),
        )
    }

    @Test
    fun `dismiss removes today progress notification`() {
        useCase.dismiss()

        verify(notificationHelper).dismissProgressNotification()
    }
}
