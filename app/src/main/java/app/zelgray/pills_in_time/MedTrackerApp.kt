package app.zelgray.pills_in_time

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.zelgray.pills_in_time.notification.DailyRescheduleWorker
import app.zelgray.pills_in_time.notification.LowStockCheckWorker
import app.zelgray.pills_in_time.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MedTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        DailyRescheduleWorker.enqueuePeriodic(this)
        LowStockCheckWorker.enqueuePeriodic(this)
    }
}
