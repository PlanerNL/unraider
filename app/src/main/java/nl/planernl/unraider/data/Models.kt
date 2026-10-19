package nl.planernl.unraider.data

import nl.planernl.unraider.R

/** Algemene laadtoestand voor een scherm. */
data class Load<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val updatedAt: Long? = null,
)

enum class Health { OK, SLEEPING, WARNING, CRITICAL }

enum class DiskRole { PARITY, DATA, CACHE, OTHER }

data class ArrayDiskInfo(
    val id: String,
    val idx: Int?,
    val name: String,
    val device: String?,
    val role: DiskRole,
    val status: String?,
    val sizeKb: Long?,
    val fsSizeKb: Long?,
    val fsUsedKb: Long?,
    val fsFreeKb: Long?,
    val fsType: String?,
    val temp: Int?,
    val rotational: Boolean?,
    val isSpinning: Boolean?,
    val numReads: Long?,
    val numWrites: Long?,
    val numErrors: Long?,
    val warningTemp: Int?,
    val criticalTemp: Int?,
    val transport: String?,
) {
    /** Unraid-id is meestal "MODEL_SERIENUMMER", eventueel met een server-prefix. */
    val identification: String
        get() = id.substringAfter(':').ifBlank { id }

    val displayName: String
        get() {
            val n = name.lowercase()
            return when {
                n == "parity" -> "Parity"
                n.startsWith("parity") -> "Parity ${n.removePrefix("parity")}"
                n.startsWith("disk") -> "Disk ${n.removePrefix("disk")}"
                else -> name.replaceFirstChar { it.uppercase() }
            }
        }

    val isSsd: Boolean get() = rotational == false

    /** Standaarddrempels van Unraid als er per schijf niets is ingesteld. */
    val effectiveWarningTemp: Int get() = warningTemp?.takeIf { it > 0 } ?: if (isSsd) 60 else 45
    val effectiveCriticalTemp: Int get() = criticalTemp?.takeIf { it > 0 } ?: if (isSsd) 70 else 55

    val usedFraction: Float?
        get() {
            val size = fsSizeKb ?: return null
            val used = fsUsedKb ?: return null
            if (size <= 0) return null
            return (used.toFloat() / size).coerceIn(0f, 1f)
        }

    val sleeping: Boolean get() = isSpinning == false && !isSsd

    val health: Health
        get() {
            val statusHealth = when (status) {
                null, "DISK_OK" -> Health.OK
                "DISK_NEW", "DISK_DSBL_NEW" -> Health.WARNING
                else -> Health.CRITICAL
            }
            val tempHealth = when {
                temp == null -> Health.OK
                temp >= effectiveCriticalTemp -> Health.CRITICAL
                temp >= effectiveWarningTemp -> Health.WARNING
                else -> Health.OK
            }
            val errorHealth = if ((numErrors ?: 0) > 0) Health.WARNING else Health.OK
            val usageHealth = if ((usedFraction ?: 0f) >= 0.9f) Health.WARNING else Health.OK

            val worst = listOf(statusHealth, tempHealth, errorHealth, usageHealth).maxBy { it.ordinal }
            return if (worst == Health.OK && sleeping) Health.SLEEPING else worst
        }

    /** Korte uitleg waarom een schijf een waarschuwing heeft. */
    val problems: List<UiText>
        get() = buildList {
            if (status != null && status != "DISK_OK") add(statusText(status))
            if (temp != null && temp >= effectiveCriticalTemp) add(UiText.Res(R.string.problem_temp_critical, temp))
            else if (temp != null && temp >= effectiveWarningTemp) add(UiText.Res(R.string.problem_temp_high, temp))
            val errors = numErrors ?: 0
            if (errors > 0) {
                val n = errors.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                add(UiText.Plural(R.plurals.problem_io_errors, n, n))
            }
            if ((usedFraction ?: 0f) >= 0.9f) add(UiText.Res(R.string.problem_almost_full, ((usedFraction ?: 0f) * 100).toInt()))
        }
}

fun statusText(status: String?): UiText = when (status) {
    null -> UiText.Res(R.string.unknown)
    "DISK_OK" -> UiText.Res(R.string.disk_status_ok)
    "DISK_NP" -> UiText.Res(R.string.disk_status_np)
    "DISK_NP_MISSING" -> UiText.Res(R.string.disk_status_np_missing)
    "DISK_INVALID" -> UiText.Res(R.string.disk_status_invalid)
    "DISK_WRONG" -> UiText.Res(R.string.disk_status_wrong)
    "DISK_DSBL" -> UiText.Res(R.string.disk_status_dsbl)
    "DISK_NP_DSBL" -> UiText.Res(R.string.disk_status_np_dsbl)
    "DISK_DSBL_NEW" -> UiText.Res(R.string.disk_status_dsbl_new)
    "DISK_NEW" -> UiText.Res(R.string.disk_status_new)
    else -> UiText.Raw(status)
}

fun arrayStateText(state: String?): UiText = when (state) {
    null -> UiText.Res(R.string.unknown)
    "STARTED" -> UiText.Res(R.string.array_state_started)
    "STOPPED" -> UiText.Res(R.string.array_state_stopped)
    "NEW_ARRAY" -> UiText.Res(R.string.array_state_new_array)
    "RECON_DISK" -> UiText.Res(R.string.array_state_recon_disk)
    "DISABLE_DISK" -> UiText.Res(R.string.array_state_disable_disk)
    "SWAP_DSBL" -> UiText.Res(R.string.array_state_swap_dsbl)
    "INVALID_EXPANSION" -> UiText.Res(R.string.array_state_invalid_expansion)
    "PARITY_NOT_BIGGEST" -> UiText.Res(R.string.array_state_parity_not_biggest)
    "TOO_MANY_MISSING_DISKS" -> UiText.Res(R.string.array_state_too_many_missing)
    "NEW_DISK_TOO_SMALL" -> UiText.Res(R.string.array_state_new_disk_too_small)
    "NO_DATA_DISKS" -> UiText.Res(R.string.array_state_no_data_disks)
    else -> UiText.Raw(state)
}

data class ParityStatus(
    val status: String?,
    val progress: Int?,
    val errors: Int?,
    val running: Boolean?,
    val paused: Boolean?,
    val correcting: Boolean?,
    val date: String?,
    val duration: Int?,
    val speed: String?,
) {
    val text: UiText
        get() = when (status) {
            "NEVER_RUN" -> UiText.Res(R.string.parity_never_run)
            "RUNNING" -> progress?.let { UiText.Res(R.string.parity_running_pct, it) } ?: UiText.Res(R.string.parity_running)
            "PAUSED" -> progress?.let { UiText.Res(R.string.parity_paused_pct, it) } ?: UiText.Res(R.string.parity_paused)
            "COMPLETED" -> UiText.Res(R.string.parity_completed)
            "CANCELLED" -> UiText.Res(R.string.parity_cancelled)
            "FAILED" -> UiText.Res(R.string.parity_failed)
            null -> UiText.Res(R.string.unknown)
            else -> UiText.Raw(status)
        }

    val isActive: Boolean get() = running == true || status == "RUNNING" || status == "PAUSED"
}

data class DiskOverview(
    val arrayState: String?,
    val capacityUsedKb: Long?,
    val capacityTotalKb: Long?,
    val parity: ParityStatus?,
    val parities: List<ArrayDiskInfo>,
    val data: List<ArrayDiskInfo>,
    val caches: List<ArrayDiskInfo>,
) {
    val all: List<ArrayDiskInfo> get() = parities + data + caches
    val warnings: Int get() = all.count { it.health == Health.WARNING || it.health == Health.CRITICAL }
    val sleepingCount: Int get() = all.count { it.sleeping }
}

/** Extra gegevens van de fysieke schijf (alleen op verzoek: kan slapende schijven wekken). */
data class PhysicalDiskInfo(
    val device: String,
    val name: String,
    val vendor: String?,
    val serial: String?,
    val firmware: String?,
    val smartStatus: String?,
    val temperature: Double?,
    val interfaceType: String?,
    val sizeBytes: Long?,
)

data class Dashboard(
    val serverName: String?,
    val version: String?,
    val bootTime: String?,
    val arrayState: String?,
    val capacityUsedKb: Long?,
    val capacityTotalKb: Long?,
    val parity: ParityStatus?,
    val cpuPercent: Double?,
    val memPercent: Double?,
    val memUsedBytes: Long?,
    val memTotalBytes: Long?,
    val unreadWarnings: Int?,
    val unreadAlerts: Int?,
    val unreadInfo: Int?,
    val partialErrors: List<UiText>,
)

data class Container(
    val id: String,
    val name: String,
    val image: String,
    val state: String,
    val status: String,
    val autoStart: Boolean?,
    val webUiUrl: String?,
    val updateAvailable: Boolean?,
) {
    val running: Boolean get() = state == "RUNNING"
    val paused: Boolean get() = state == "PAUSED"
}

data class Vm(
    val id: String,
    val name: String,
    val state: String,
) {
    val running: Boolean get() = state == "RUNNING" || state == "IDLE"
    val paused: Boolean get() = state == "PAUSED" || state == "PMSUSPENDED"

    val stateText: UiText
        get() = when (state) {
            "RUNNING" -> UiText.Res(R.string.vm_state_running)
            "IDLE" -> UiText.Res(R.string.vm_state_idle)
            "PAUSED" -> UiText.Res(R.string.vm_state_paused)
            "SHUTDOWN" -> UiText.Res(R.string.vm_state_shutdown)
            "SHUTOFF" -> UiText.Res(R.string.vm_state_shutoff)
            "CRASHED" -> UiText.Res(R.string.vm_state_crashed)
            "PMSUSPENDED" -> UiText.Res(R.string.vm_state_suspended)
            else -> UiText.Raw(state)
        }
}

// ---- Opmaak ----

/** Unraid geeft schijfgroottes in KiB; weergave decimaal zoals in de webGUI (8 TB). */
fun formatKb(kb: Long?): String = formatBytes(kb?.let { it * 1024 })

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "–"
    val units = listOf("B", "kB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1000 && i < units.lastIndex) {
        value /= 1000
        i++
    }
    return if (value >= 100 || i == 0) "%.0f %s".format(value, units[i])
    else "%.1f %s".format(value, units[i])
}

fun formatCount(n: Long?): String {
    if (n == null) return "–"
    return when {
        n >= 1_000_000_000 -> "%.1fG".format(n / 1e9)
        n >= 1_000_000 -> "%.1fM".format(n / 1e6)
        n >= 1_000 -> "%.1fk".format(n / 1e3)
        else -> n.toString()
    }
}
