package nl.planernl.unraider.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.planernl.unraider.R
import nl.planernl.unraider.data.CertInfo
import nl.planernl.unraider.data.CertificateNotTrustedException
import nl.planernl.unraider.data.Container
import nl.planernl.unraider.data.HttpsRedirectException
import nl.planernl.unraider.data.ContainerAction
import nl.planernl.unraider.data.Dashboard
import nl.planernl.unraider.data.DiskOverview
import nl.planernl.unraider.data.Load
import nl.planernl.unraider.data.ParityAction
import nl.planernl.unraider.data.PhysicalDiskInfo
import nl.planernl.unraider.data.ServerConfig
import nl.planernl.unraider.data.SettingsStore
import nl.planernl.unraider.data.UnraidClient
import nl.planernl.unraider.data.UnraidRepository
import nl.planernl.unraider.data.UiText
import nl.planernl.unraider.data.Vm
import nl.planernl.unraider.data.VmAction
import nl.planernl.unraider.data.toUiText

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    /** null = nog aan het laden uit de opslag. */
    private val _config = MutableStateFlow<ServerConfig?>(null)
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    private var repo: UnraidRepository? = null

    private val _dashboard = MutableStateFlow(Load<Dashboard>())
    val dashboard = _dashboard.asStateFlow()

    private val _disks = MutableStateFlow(Load<DiskOverview>())
    val disks = _disks.asStateFlow()

    private val _smart = MutableStateFlow(Load<List<PhysicalDiskInfo>>())
    val smart = _smart.asStateFlow()

    private val _containers = MutableStateFlow(Load<List<Container>>())
    val containers = _containers.asStateFlow()

    private val _vms = MutableStateFlow(Load<List<Vm>>())
    val vms = _vms.asStateFlow()

    /** Ids van items waarvoor een actie loopt (voor spinners op knoppen). */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            settings.config.collect { cfg ->
                if (cfg != _config.value) {
                    _config.value = cfg
                    repo = if (cfg.isComplete) UnraidRepository(UnraidClient(cfg)) else null
                    resetData()
                }
            }
        }
    }

    private fun resetData() {
        _dashboard.value = Load()
        _disks.value = Load()
        _smart.value = Load()
        _containers.value = Load()
        _vms.value = Load()
    }

    // ---------- Instellingen ----------

    sealed interface TestResult {
        /** [config] kan afwijken van wat je invulde (bijv. omgezet naar https). */
        data class Success(val serverName: UiText, val config: ServerConfig) : TestResult
        data class Untrusted(val cert: CertInfo, val pinChanged: Boolean, val config: ServerConfig) : TestResult
        data class Failure(val error: UiText, val config: ServerConfig) : TestResult
    }

    /** Test de verbinding; volgt zelf een omleiding naar https en meldt onbekende certificaten. */
    suspend fun testConnection(candidate: ServerConfig): TestResult {
        var cfg = candidate
        repeat(2) {
            try {
                return TestResult.Success(UnraidRepository(UnraidClient(cfg)).testConnection(), cfg)
            } catch (e: HttpsRedirectException) {
                if (cfg.baseUrl == e.newBaseUrl) return TestResult.Failure(e.text, cfg)
                cfg = cfg.copy(address = e.newBaseUrl)
            } catch (e: CertificateNotTrustedException) {
                return TestResult.Untrusted(e.cert, e.pinChanged, cfg)
            } catch (e: Exception) {
                return TestResult.Failure(e.toUiText(), cfg)
            }
        }
        return TestResult.Failure(UiText.Res(R.string.err_redirect, cfg.baseUrl), cfg)
    }

    fun saveConfig(cfg: ServerConfig) {
        viewModelScope.launch { settings.save(cfg) }
    }

    // ---------- Laden ----------

    private fun <T> load(target: MutableStateFlow<Load<T>>, block: suspend (UnraidRepository) -> T) {
        val r = repo ?: return
        if (target.value.loading) return
        target.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val result = block(r)
                target.value = Load(data = result, updatedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                // Oude gegevens laten staan, alleen de fout tonen
                target.update { it.copy(loading = false, error = e.toUiText()) }
            }
        }
    }

    fun refreshDashboard() = load(_dashboard) { it.dashboard() }
    fun refreshDisks() = load(_disks) { it.diskOverview() }
    fun loadSmart() = load(_smart) { it.physicalDisks() }
    fun refreshContainers() = load(_containers) { it.containers() }
    fun refreshVms() = load(_vms) { it.vms() }

    // ---------- Acties ----------

    private fun action(
        key: String,
        successMessage: UiText,
        afterwards: () -> Unit,
        block: suspend (UnraidRepository) -> Unit,
    ) {
        val r = repo ?: return
        _busy.update { it + key }
        viewModelScope.launch {
            try {
                block(r)
                _messages.send(successMessage)
            } catch (e: Exception) {
                _messages.send(UiText.Res(R.string.msg_failed, e.toUiText()))
            } finally {
                _busy.update { it - key }
                afterwards()
            }
        }
    }

    fun containerAction(c: Container, a: ContainerAction) {
        val msg = when (a) {
            ContainerAction.START -> R.string.msg_container_started
            ContainerAction.STOP -> R.string.msg_container_stopped
            ContainerAction.RESTART -> R.string.msg_container_restarted
            ContainerAction.PAUSE -> R.string.msg_container_paused
            ContainerAction.UNPAUSE -> R.string.msg_container_resumed
        }
        action(c.id, UiText.Res(msg, c.name), ::refreshContainers) { it.containerAction(c.id, a) }
    }

    fun vmAction(vm: Vm, a: VmAction) {
        val msg = when (a) {
            VmAction.START -> R.string.msg_vm_started
            VmAction.STOP -> R.string.msg_vm_stopping
            VmAction.FORCE_STOP -> R.string.msg_vm_force_stopped
            VmAction.PAUSE -> R.string.msg_vm_paused
            VmAction.RESUME -> R.string.msg_vm_resumed
            VmAction.REBOOT -> R.string.msg_vm_rebooting
        }
        action(vm.id, UiText.Res(msg, vm.name), ::refreshVms) { it.vmAction(vm.id, a) }
    }

    fun setArrayState(start: Boolean) {
        action(
            "array",
            UiText.Res(if (start) R.string.msg_array_starting else R.string.msg_array_stopping),
            { refreshDisks(); refreshDashboard() },
        ) { it.setArrayState(start) }
    }

    fun parityAction(a: ParityAction) {
        val msg = when (a) {
            ParityAction.START_CHECK -> R.string.msg_parity_started_check
            ParityAction.START_CORRECT -> R.string.msg_parity_started_correct
            ParityAction.PAUSE -> R.string.msg_parity_paused
            ParityAction.RESUME -> R.string.msg_parity_resumed
            ParityAction.CANCEL -> R.string.msg_parity_cancelled
        }
        action("parity", UiText.Res(msg), { refreshDisks() }) { it.parityAction(a) }
    }
}
