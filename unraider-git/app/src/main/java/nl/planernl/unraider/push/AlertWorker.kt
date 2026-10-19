package nl.planernl.unraider.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.planernl.unraider.MainActivity
import nl.planernl.unraider.R
import nl.planernl.unraider.data.Health
import nl.planernl.unraider.data.SettingsStore
import nl.planernl.unraider.data.UnraidClient
import nl.planernl.unraider.data.UnraidRepository
import java.util.concurrent.TimeUnit

/**
 * Controleert op de achtergrond (elke 15 minuten, de kortste interval die Android toestaat)
 * op nieuwe Unraid-waarschuwingen en schijfproblemen, en toont die als melding.
 *
 * Werkt alleen als de telefoon de server kan bereiken, dus met Tailscale verbonden.
 * Lukt dat niet, dan wordt de controle stil overgeslagen.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        if (!settings.pushEnabled.first()) return Result.success()
        val cfg = settings.currentConfig()
        if (!cfg.isComplete) return Result.success()

        val repo = UnraidRepository(UnraidClient(cfg))
        val alerts = runCatching { repo.unreadAlerts() }.getOrNull()
        val disks = runCatching { repo.diskOverview() }.getOrNull()
        if (alerts == null && disks == null) return Result.success() // server niet bereikbaar

        val res = applicationContext.resources
        val current = mutableMapOf<String, Pair<String, String>>() // sleutel -> titel, tekst

        alerts?.forEach { a ->
            current["n:${a.id}"] = a.subject.ifBlank { a.title } to a.description.ifBlank { a.title }
        }
        disks?.all?.forEach { d ->
            val h = d.health
            if (h == Health.WARNING || h == Health.CRITICAL) {
                val problem = d.problems.firstOrNull()?.resolve(res) ?: ""
                current["d:${d.name}:$h"] = res.getString(R.string.push_disk_title, d.displayName) to problem
            }
        }

        val seen = settings.seenKeys()
        val fresh = current.filterKeys { it !in seen }
        if (fresh.isNotEmpty()) show(fresh.values.toList())

        // Alleen actuele sleutels bewaren: een opgelost probleem mag later opnieuw melden.
        settings.setSeenKeys(current.keys)
        return Result.success()
    }

    private fun show(items: List<Pair<String, String>>) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nm = NotificationManagerCompat.from(ctx)
        try {
            items.take(MAX_SINGLE).forEachIndexed { i, (title, text) ->
                val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_unraider)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setColor(0xFFF26B2A.toInt())
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setGroup(GROUP)
                    .build()
                nm.notify(NOTIFICATION_BASE + i, n)
            }
            if (items.size > MAX_SINGLE) {
                val extra = items.size - MAX_SINGLE
                val summary = NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_unraider)
                    .setContentTitle(ctx.resources.getQuantityString(R.plurals.push_more, extra, extra))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setGroup(GROUP)
                    .setGroupSummary(true)
                    .build()
                nm.notify(NOTIFICATION_BASE + MAX_SINGLE, summary)
            }
        } catch (e: SecurityException) {
            // Toestemming ingetrokken
        }
    }

    companion object {
        const val CHANNEL_ID = "unraid_alerts"
        private const val GROUP = "unraider_alerts"
        private const val WORK_NAME = "unraider-alerts"
        private const val NOTIFICATION_BASE = 1000
        private const val MAX_SINGLE = 5

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.push_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = ctx.getString(R.string.push_channel_description) }
            )
        }

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}
