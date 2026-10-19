package nl.planernl.unraider.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.planernl.unraider.R
import nl.planernl.unraider.data.CertInfo
import nl.planernl.unraider.data.ServerConfig
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.ui.AppLanguage
import nl.planernl.unraider.ui.Chassis
import nl.planernl.unraider.ui.ChassisRow
import nl.planernl.unraider.ui.HeaderIconButton
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.Led
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.Panel
import nl.planernl.unraider.ui.RackSwitch
import nl.planernl.unraider.ui.RowDivider
import nl.planernl.unraider.ui.ScreenHeader
import nl.planernl.unraider.ui.SectionLabel
import nl.planernl.unraider.ui.SmallSpinner
import nl.planernl.unraider.ui.theme.Rack
import java.text.DateFormat

@Composable
fun SetupScreen(vm: MainViewModel, current: ServerConfig, onDone: (() -> Unit)? = null) {
    var address by rememberSaveable { mutableStateOf(current.address) }
    var apiKey by rememberSaveable { mutableStateOf(current.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var certPin by rememberSaveable { mutableStateOf(current.certPin) }
    var result by remember { mutableStateOf<MainViewModel.TestResult?>(null) }
    var redirectedTo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstRun = !current.isComplete

    val candidate = ServerConfig(address, apiKey, certPin)

    fun runTest(cfg: ServerConfig) {
        testing = true
        scope.launch {
            val r = vm.testConnection(cfg)
            // Server stuurde door naar https: neem dat adres over
            if (r.config().address != cfg.address) {
                address = r.config().address
                redirectedTo = r.config().address
            }
            result = r
            testing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(if (firstRun) R.string.setup_title else R.string.settings)) {
            if (onDone != null) {
                HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onDone)
            }
        }

        if (firstRun) {
            Panel { Text(stringResource(R.string.setup_steps), style = MaterialTheme.typography.bodyMedium) }
        }

        SectionLabel(stringResource(R.string.section_connection))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; result = null; redirectedTo = null },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
            supportingText = { Text(stringResource(R.string.server_address_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; result = null },
            label = { Text(stringResource(R.string.api_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (showKey) R.string.hide else R.string.show),
                        tint = Rack.textSecondary,
                    )
                }
            },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = candidate.isComplete && !testing,
                onClick = { runTest(candidate) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Rack.text),
                border = BorderStroke(1.dp, Rack.border),
            ) { Text(stringResource(R.string.test_connection)) }
            Button(
                enabled = candidate.isComplete && !testing,
                onClick = {
                    vm.saveConfig(candidate)
                    onDone?.invoke()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Rack.accent, contentColor = Rack.onAccent),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
            if (testing) SmallSpinner()
        }

        redirectedTo?.let {
            Text(stringResource(R.string.redirected_to_https, it), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
        }

        when (val r = result) {
            is MainViewModel.TestResult.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                Led(Rack.ok)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.connected_to, r.serverName.asString()), style = MaterialTheme.typography.bodyMedium)
            }
            is MainViewModel.TestResult.Failure -> InlineError(r.error.asString())
            is MainViewModel.TestResult.Untrusted -> UntrustedCertCard(
                cert = r.cert,
                pinChanged = r.pinChanged,
                onTrust = {
                    certPin = r.cert.fingerprint
                    runTest(r.config.copy(certPin = r.cert.fingerprint))
                },
            )
            null -> {}
        }

        certPin?.let { pin -> TrustedCertRow(pin, onForget = { certPin = null; result = null }) }

        Text(stringResource(R.string.setup_footer), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)

        if (!firstRun) {
            PushSection(vm)
        }
        LanguageSection()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Rack.accent,
    unfocusedBorderColor = Rack.border,
    focusedLabelColor = Rack.accent,
    unfocusedLabelColor = Rack.textSecondary,
    cursorColor = Rack.accent,
    focusedContainerColor = Rack.chassis,
    unfocusedContainerColor = Rack.chassis,
)

@Composable
private fun PushSection(vm: MainViewModel) {
    val enabled by vm.pushEnabled.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setPushEnabled(true)
    }

    SectionLabel(stringResource(R.string.section_push))
    Chassis {
        ChassisRow {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.push_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.push_text), style = MaterialTheme.typography.bodySmall, color = Rack.textSecondary)
            }
            RackSwitch(
                checked = enabled,
                label = stringResource(R.string.push_title),
                onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setPushEnabled(on)
                    }
                },
            )
        }
    }
}

@Composable
private fun LanguageSection() {
    // Na een wissel wordt de activity opnieuw opgebouwd, dus dit leest steeds de actuele keuze
    val selected = remember { AppLanguage.current() }
    SectionLabel(stringResource(R.string.language))
    Chassis {
        LanguageRow(stringResource(R.string.language_system), AppLanguage.SYSTEM, selected)
        RowDivider()
        // Talen altijd in hun eigen naam, zodat je ze herkent in een taal die je niet leest
        LanguageRow("English", AppLanguage.ENGLISH, selected)
        RowDivider()
        LanguageRow("Nederlands", AppLanguage.DUTCH, selected)
    }
}

@Composable
private fun LanguageRow(label: String, language: AppLanguage, selected: AppLanguage) {
    val active = language == selected
    ChassisRow(onClick = { if (!active) AppLanguage.apply(language) }) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (active) Led(Rack.accent, 9.dp)
    }
}

private fun MainViewModel.TestResult.config(): ServerConfig = when (this) {
    is MainViewModel.TestResult.Success -> config
    is MainViewModel.TestResult.Untrusted -> config
    is MainViewModel.TestResult.Failure -> config
}

@Composable
private fun UntrustedCertCard(cert: CertInfo, pinChanged: Boolean, onTrust: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Led(if (pinChanged) Rack.critical else Rack.warning)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (pinChanged) R.string.cert_changed_title else R.string.cert_untrusted_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (pinChanged) R.string.cert_changed_text else R.string.cert_untrusted_text),
            style = MaterialTheme.typography.bodySmall,
            color = Rack.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        CertLine(stringResource(R.string.cert_subject), cert.subject)
        CertLine(stringResource(R.string.cert_valid_until), dateFormat.format(cert.validUntil))
        CertLine(stringResource(R.string.cert_fingerprint), cert.fingerprint, mono = true)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onTrust,
            colors = ButtonDefaults.buttonColors(containerColor = Rack.accent, contentColor = Rack.onAccent),
        ) { Text(stringResource(R.string.cert_trust), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun TrustedCertRow(pin: String, onForget: () -> Unit) {
    Chassis {
        ChassisRow {
            Led(Rack.ok)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cert_trusted), style = MaterialTheme.typography.titleSmall)
                Text(pin.take(23) + "…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Rack.textSecondary)
            }
            TextButton(onClick = onForget) { Text(stringResource(R.string.cert_forget), color = Rack.accent) }
        }
    }
}

@Composable
private fun CertLine(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Rack.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}
