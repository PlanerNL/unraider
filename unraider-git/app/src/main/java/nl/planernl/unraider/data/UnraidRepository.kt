package nl.planernl.unraider.data

import androidx.annotation.StringRes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.planernl.unraider.R

/**
 * Alle queries en acties richting Unraid.
 *
 * Belangrijk: het schijvenoverzicht gebruikt alleen `array { ... }`. Die gegevens
 * komen uit de status van emhttp en wekken géén slapende schijven. De query
 * `disks { ... }` roept SMART-opdrachten aan en kan schijven laten opspinnen;
 * die wordt daarom alleen op jouw verzoek uitgevoerd (knop "SMART-details").
 */
class UnraidRepository(private val client: UnraidClient) {

    // ---------- Verbinding ----------

    /** Geeft de servernaam terug, of gooit een UnraidException met uitleg. */
    suspend fun testConnection(): UiText {
        val data = client.query("query { online }")
        if (data.bool("online") != true) throw UnraidException(UiText.Res(R.string.err_server_offline))
        val name = try {
            client.query("query { vars { name version } }").obj("vars")
                ?.let { v -> listOfNotNull(v.str("name"), v.str("version")?.let { "Unraid $it" }).joinToString(" · ") }
                ?.ifBlank { null }
        } catch (e: UnraidException) {
            null
        }
        return name?.let { UiText.Raw(it) } ?: UiText.Res(R.string.conn_connected)
    }

    // ---------- Schijven ----------

    suspend fun diskOverview(): DiskOverview = coroutineScope {
        val disksJob = async {
            client.queryWithFallback(
                primary = """
                    query Schijven {
                      array {
                        state
                        capacity { kilobytes { free used total } }
                        parities { ...D }
                        disks { ...D }
                        caches { ...D }
                      }
                    }
                    fragment D on ArrayDisk { $DISK_FIELDS_FULL }
                """.trimIndent(),
                fallback = """
                    query Schijven {
                      array {
                        state
                        capacity { kilobytes { free used total } }
                        parities { ...D }
                        disks { ...D }
                        caches { ...D }
                      }
                    }
                    fragment D on ArrayDisk { $DISK_FIELDS_BASIC }
                """.trimIndent(),
            )
        }
        val parityJob = async { runCatching { parityStatus() }.getOrNull() }

        val array = disksJob.await().obj("array") ?: throw UnraidException(UiText.Res(R.string.err_no_array_data))
        val kb = array.obj("capacity")?.obj("kilobytes")

        DiskOverview(
            arrayState = array.str("state"),
            capacityUsedKb = kb?.long("used"),
            capacityTotalKb = kb?.long("total"),
            parity = parityJob.await(),
            parities = array.objects("parities").mapNotNull { parseArrayDisk(it, DiskRole.PARITY) },
            data = array.objects("disks").mapNotNull { parseArrayDisk(it, DiskRole.DATA) },
            caches = array.objects("caches").mapNotNull { parseArrayDisk(it, DiskRole.CACHE) },
        )
    }

    private fun parseArrayDisk(o: JsonObject, defaultRole: DiskRole): ArrayDiskInfo? {
        val status = o.str("status")
        val device = o.str("device")
        // Lege slots overslaan
        if (status == "DISK_NP" && device.isNullOrBlank()) return null
        val role = when (o.str("type")) {
            "PARITY" -> DiskRole.PARITY
            "DATA" -> DiskRole.DATA
            "CACHE" -> DiskRole.CACHE
            null -> defaultRole
            else -> DiskRole.OTHER
        }
        return ArrayDiskInfo(
            id = o.str("id").orEmpty(),
            idx = o.int("idx"),
            name = o.str("name") ?: "?",
            device = device,
            role = role,
            status = status,
            sizeKb = o.long("size"),
            fsSizeKb = o.long("fsSize"),
            fsUsedKb = o.long("fsUsed"),
            fsFreeKb = o.long("fsFree"),
            fsType = o.str("fsType"),
            temp = o.int("temp"),
            rotational = o.bool("rotational"),
            isSpinning = o.bool("isSpinning"),
            numReads = o.long("numReads"),
            numWrites = o.long("numWrites"),
            numErrors = o.long("numErrors"),
            warningTemp = o.int("warning"),
            criticalTemp = o.int("critical"),
            transport = o.str("transport"),
        )
    }

    suspend fun parityStatus(): ParityStatus? {
        val data = client.queryWithFallback(
            primary = "query { array { parityCheckStatus { status progress errors running paused correcting date duration speed } } }",
            fallback = "query { array { parityCheckStatus { status progress errors } } }",
        )
        val p = data.obj("array")?.obj("parityCheckStatus") ?: return null
        return ParityStatus(
            status = p.str("status"),
            progress = p.int("progress"),
            errors = p.int("errors"),
            running = p.bool("running"),
            paused = p.bool("paused"),
            correcting = p.bool("correcting"),
            date = p.str("date"),
            duration = p.int("duration"),
            speed = p.str("speed"),
        )
    }

    /** Let op: kan slapende schijven wekken. */
    suspend fun physicalDisks(): List<PhysicalDiskInfo> {
        val data = client.query(
            "query { disks { device name vendor serialNum firmwareRevision smartStatus temperature interfaceType size } }"
        )
        return data.objects("disks").map { d ->
            PhysicalDiskInfo(
                device = d.str("device").orEmpty(),
                name = d.str("name").orEmpty(),
                vendor = d.str("vendor"),
                serial = d.str("serialNum"),
                firmware = d.str("firmwareRevision"),
                smartStatus = d.str("smartStatus"),
                temperature = d.double("temperature"),
                interfaceType = d.str("interfaceType"),
                sizeBytes = d.long("size"),
            )
        }
    }

    suspend fun setArrayState(start: Boolean) {
        val state = if (start) "START" else "STOP"
        client.query("mutation { array { setState(input: { desiredState: $state }) { state } } }")
    }

    suspend fun parityAction(action: ParityAction) {
        val field = when (action) {
            ParityAction.START_CHECK -> "start(correct: false)"
            ParityAction.START_CORRECT -> "start(correct: true)"
            ParityAction.PAUSE -> "pause"
            ParityAction.RESUME -> "resume"
            ParityAction.CANCEL -> "cancel"
        }
        client.query("mutation { parityCheck { $field } }")
    }

    // ---------- Dashboard ----------

    suspend fun dashboard(): Dashboard = coroutineScope {
        val errors = mutableListOf<UiText>()

        val varsJob = async { runCatching { client.query("query { vars { name version } }").obj("vars") } }
        val osJob = async { runCatching { client.query("query { info { os { hostname uptime } } }").obj("info")?.obj("os") } }
        val arrayJob = async {
            runCatching { client.query("query { array { state capacity { kilobytes { used total } } } }").obj("array") }
        }
        val parityJob = async { runCatching { parityStatus() } }
        val metricsJob = async {
            runCatching {
                client.query("query { metrics { cpu { percentTotal } memory { total used percentTotal } } }").obj("metrics")
            }
        }
        val notifJob = async {
            runCatching {
                client.query("query { notifications { overview { unread { info warning alert } } } }")
                    .obj("notifications")?.obj("overview")?.obj("unread")
            }
        }

        fun section(@StringRes label: Int, e: Throwable) = UiText.Res(R.string.err_section, UiText.Res(label), e.toUiText())

        var firstError: UiText? = null
        val vars = varsJob.await().onFailure { errors += section(R.string.section_server, it); firstError = firstError ?: it.toUiText() }.getOrNull()
        val os = osJob.await().getOrNull()
        val array = arrayJob.await().onFailure { errors += section(R.string.section_array, it); firstError = firstError ?: it.toUiText() }.getOrNull()
        val parity = parityJob.await().getOrNull()
        val metrics = metricsJob.await().onFailure { errors += section(R.string.section_metrics, it); firstError = firstError ?: it.toUiText() }.getOrNull()
        val unread = notifJob.await().getOrNull()

        if (vars == null && array == null && metrics == null) {
            throw UnraidException(firstError ?: UiText.Res(R.string.err_no_data))
        }

        val kb = array?.obj("capacity")?.obj("kilobytes")
        val mem = metrics?.obj("memory")
        Dashboard(
            serverName = vars?.str("name") ?: os?.str("hostname"),
            version = vars?.str("version"),
            bootTime = os?.str("uptime"),
            arrayState = array?.str("state"),
            capacityUsedKb = kb?.long("used"),
            capacityTotalKb = kb?.long("total"),
            parity = parity,
            cpuPercent = metrics?.obj("cpu")?.double("percentTotal"),
            memPercent = mem?.double("percentTotal"),
            memUsedBytes = mem?.long("used"),
            memTotalBytes = mem?.long("total"),
            unreadWarnings = unread?.int("warning"),
            unreadAlerts = unread?.int("alert"),
            unreadInfo = unread?.int("info"),
            partialErrors = errors,
        )
    }

    // ---------- Docker ----------

    suspend fun containers(): List<Container> {
        val data = client.queryWithFallback(
            primary = "query { docker { containers { id names image state status autoStart webUiUrl isUpdateAvailable iconUrl } } }",
            fallback = "query { docker { containers { id names image state status autoStart } } }",
        )
        return data.obj("docker")?.objects("containers").orEmpty()
            .map { c ->
                Container(
                    id = c.str("id").orEmpty(),
                    name = c.strings("names").firstOrNull()?.removePrefix("/") ?: "?",
                    image = c.str("image").orEmpty(),
                    state = c.str("state").orEmpty(),
                    status = c.str("status").orEmpty(),
                    autoStart = c.bool("autoStart"),
                    webUiUrl = c.str("webUiUrl")?.ifBlank { null },
                    updateAvailable = c.bool("isUpdateAvailable"),
                    iconUrl = c.str("iconUrl")?.takeIf { it.startsWith("http") || it.startsWith("/") },
                )
            }
            .sortedWith(compareBy<Container> { !it.running }.thenBy { it.name.lowercase() })
    }

    suspend fun containerAction(id: String, action: ContainerAction) {
        val field = when (action) {
            ContainerAction.START -> "start"
            ContainerAction.STOP -> "stop"
            ContainerAction.RESTART -> "restart"
            ContainerAction.PAUSE -> "pause"
            ContainerAction.UNPAUSE -> "unpause"
        }
        client.query(
            "mutation(\$id: PrefixedID!) { docker { $field(id: \$id) { id state } } }",
            idVariable(id),
        )
    }

    // ---------- VM's ----------

    suspend fun vms(): List<Vm> {
        val data = client.query("query { vms { domains { id name state } } }")
        return data.obj("vms")?.objects("domains").orEmpty()
            .map { Vm(id = it.str("id").orEmpty(), name = it.str("name") ?: "?", state = it.str("state").orEmpty()) }
            .sortedWith(compareBy<Vm> { !it.running }.thenBy { it.name.lowercase() })
    }

    suspend fun vmAction(id: String, action: VmAction) {
        val field = when (action) {
            VmAction.START -> "start"
            VmAction.STOP -> "stop"
            VmAction.FORCE_STOP -> "forceStop"
            VmAction.PAUSE -> "pause"
            VmAction.RESUME -> "resume"
            VmAction.REBOOT -> "reboot"
        }
        client.query("mutation(\$id: PrefixedID!) { vm { $field(id: \$id) } }", idVariable(id))
    }

    // ---------- Live metrics ----------

    /**
     * Eén meting. Netwerk en temperaturen zitten pas in nieuwere API-versies;
     * ontbreken ze, dan valt de query terug op alleen CPU en geheugen.
     */
    suspend fun metricsSample(): MetricsSample {
        val data = client.queryWithFallback(
            primary = """
                query {
                  metrics {
                    cpu { percentTotal }
                    memory { total used percentTotal }
                    network { name rxSec txSec }
                    temperature { sensors { name type current { value } } }
                  }
                }
            """.trimIndent(),
            fallback = "query { metrics { cpu { percentTotal } memory { total used percentTotal } } }",
        )
        val m = data.obj("metrics") ?: throw UnraidException(UiText.Res(R.string.err_no_data))
        val mem = m.obj("memory")
        val nets = m.objects("network").filter { it.str("name") != "lo" }
        val sensors = m.obj("temperature")?.objects("sensors").orEmpty()
        fun sensor(vararg types: String) = sensors
            .filter { it.str("type") in types }
            .mapNotNull { it.obj("current")?.double("value") }
            .maxOrNull()

        return MetricsSample(
            time = System.currentTimeMillis(),
            cpuPercent = m.obj("cpu")?.double("percentTotal"),
            memPercent = mem?.double("percentTotal"),
            memUsedBytes = mem?.long("used"),
            memTotalBytes = mem?.long("total"),
            rxBytesPerSec = nets.takeIf { it.isNotEmpty() }?.sumOf { it.double("rxSec") ?: 0.0 },
            txBytesPerSec = nets.takeIf { it.isNotEmpty() }?.sumOf { it.double("txSec") ?: 0.0 },
            cpuTemp = sensor("CPU_PACKAGE", "CPU_CORE"),
            boardTemp = sensor("MOTHERBOARD", "CHIPSET"),
        )
    }

    /** Naam, versie, opstarttijd en aantallen ongelezen meldingen. */
    suspend fun serverInfo(): ServerInfo = coroutineScope {
        val varsJob = async { runCatching { client.query("query { vars { name version } }").obj("vars") }.getOrNull() }
        val osJob = async { runCatching { client.query("query { info { os { hostname uptime } } }").obj("info")?.obj("os") }.getOrNull() }
        val notifJob = async {
            runCatching {
                client.query("query { notifications { overview { unread { info warning alert } } } }")
                    .obj("notifications")?.obj("overview")?.obj("unread")
            }.getOrNull()
        }
        val vars = varsJob.await()
        val os = osJob.await()
        val unread = notifJob.await()
        if (vars == null && os == null && unread == null) throw UnraidException(UiText.Res(R.string.err_no_data))
        ServerInfo(
            name = vars?.str("name") ?: os?.str("hostname"),
            version = vars?.str("version"),
            bootTime = os?.str("uptime"),
            unreadAlerts = unread?.int("alert") ?: 0,
            unreadWarnings = unread?.int("warning") ?: 0,
            unreadInfo = unread?.int("info") ?: 0,
        )
    }

    // ---------- Shares ----------

    suspend fun shares(): List<ShareInfo> {
        val data = client.queryWithFallback(
            primary = "query { shares { name comment used free cache } }",
            fallback = "query { shares { name used free } }",
        )
        return data.objects("shares")
            .mapNotNull { s ->
                val name = s.str("name") ?: return@mapNotNull null
                ShareInfo(
                    name = name,
                    comment = s.str("comment")?.ifBlank { null },
                    usedKb = s.long("used"),
                    freeKb = s.long("free"),
                    usesCache = s.bool("cache"),
                )
            }
            .sortedByDescending { it.usedKb ?: 0 }
    }

    // ---------- Plugins ----------

    suspend fun plugins(): List<PluginInfo> {
        val data = client.query("query { installedUnraidPlugins }")
        return data.strings("installedUnraidPlugins")
            .filter { it.endsWith(".plg") }
            .map { PluginInfo(it) }
            .sortedBy { it.displayName.lowercase() }
    }

    // ---------- Meldingen ----------

    suspend fun unreadNotifications(limit: Int = 100): List<NotificationItem> {
        val data = client.query(
            """
            query {
              notifications {
                list(filter: { type: UNREAD, offset: 0, limit: $limit }) {
                  id title subject description importance timestamp formattedTimestamp link
                }
              }
            }
            """.trimIndent()
        )
        return data.obj("notifications")?.objects("list").orEmpty().map(::parseNotification)
    }

    /** Alleen ongelezen waarschuwingen en alarmen, voor de pushmeldingen. */
    suspend fun unreadAlerts(): List<NotificationItem> =
        try {
            val data = client.query(
                "query { notifications { warningsAndAlerts { id title subject description importance timestamp formattedTimestamp link } } }"
            )
            data.obj("notifications")?.objects("warningsAndAlerts").orEmpty().map(::parseNotification)
        } catch (e: UnraidException) {
            // Oudere API zonder warningsAndAlerts
            unreadNotifications(50).filter { it.importance != "INFO" }
        }

    private fun parseNotification(o: JsonObject) = NotificationItem(
        id = o.str("id").orEmpty(),
        title = o.str("title").orEmpty(),
        subject = o.str("subject").orEmpty(),
        description = o.str("description").orEmpty(),
        importance = o.str("importance") ?: "INFO",
        timestamp = o.str("timestamp"),
        formattedTimestamp = o.str("formattedTimestamp"),
        link = o.str("link")?.ifBlank { null },
    )

    suspend fun archiveNotification(id: String) {
        client.query("mutation(\$id: PrefixedID!) { archiveNotification(id: \$id) { id } }", idVariable(id))
    }

    suspend fun archiveAllNotifications() {
        client.query("mutation { archiveAll { unread { total } } }")
    }

    private fun idVariable(id: String) = buildJsonObject { put("id", id) }

    private companion object {
        const val DISK_FIELDS_BASIC =
            "id idx name device size status temp numReads numWrites numErrors fsSize fsFree fsUsed type fsType"
        const val DISK_FIELDS_FULL =
            "$DISK_FIELDS_BASIC rotational isSpinning warning critical transport"
    }
}

enum class ContainerAction { START, STOP, RESTART, PAUSE, UNPAUSE }
enum class VmAction { START, STOP, FORCE_STOP, PAUSE, RESUME, REBOOT }
enum class ParityAction { START_CHECK, START_CORRECT, PAUSE, RESUME, CANCEL }
