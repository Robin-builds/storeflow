package cl.stockflow.warehouse.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PullTrigger @Inject constructor(@ApplicationContext private val context: Context) {
    fun trigger() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PullWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PullWorker>()
                .setConstraints(constraints)
                .build()
        )
    }
}
