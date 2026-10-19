package nl.planernl.unraider.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import nl.planernl.unraider.data.CertInfo
import java.text.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.planernl.unraider.R
import nl.planernl.unraider.data.ServerConfig
import nl.planernl.unraider.data.asString
import nl.planernl.unraider.ui.AppLanguage
import nl.planernl.unraider.ui.InlineError
import nl.planernl.unraider.ui.MainViewModel
import nl.planernl.unraider.ui.SectionCard
import nl.planernl.unraider.ui.theme.StatusColors

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard {
            Text(stringResource(R.string.setup_steps), style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedTextField(
            value = address,
            onValueChange = { address = it; result = null; redirectedTo = null },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
            supportingText = { Text(stringResource(R.string.server_address_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = candidate.isComplete && !testing,
                onClick = { runTest(candidate) },
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.test_connection))
            }
            Button(
                enabled = candidate.isComplete && !testing,
                onClick = {
                    vm.saveConfig(candidate)
                    onDone?.invoke()
                },
            ) { Text(stringResource(R.string.save)) }
        }

        redirectedTo?.let {
            Text(
                stringResource(R.string.redirected_to_https, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val r = result) {
            is MainViewModel.TestResult.Success -> Text(
                stringResource(R.string.connected_to, r.serverName.asString()),
                color = StatusColors.ok,
                style = MaterialTheme.typography.bodyMedium,
            )
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

        certPin?.let { pin ->
            TrustedCertRow(pin, onForget = { certPin = null; result = null })
        }

        Text(
            stringResource(R.string.setup_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Taal
        Spacer(Modifier.height(8.dp))
        LanguagePicker()
    }
}

@Composable
private fun LanguagePicker() {
    // Na een wissel wordt de activity opnieuw opgebouwd, dus dit leest steeds de actuele keuze
    val selected = remember { AppLanguage.current() }
    SectionCard {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChip(stringResource(R.string.language_system), AppLanguage.SYSTEM, selected)
            // Talen altijd in hun eigen naam, zodat je ze herkent in een taal die je niet leest
            LanguageChip("English", AppLanguage.ENGLISH, selected)
            LanguageChip("Nederlands", AppLanguage.DUTCH, selected)
        }
    }
}

@Composable
private fun LanguageChip(label: String, language: AppLanguage, selected: AppLanguage) {
    FilterChip(
        selected = language == selected,
        onClick = { if (language != selected) AppLanguage.apply(language) },
        label = { Text(label) },
    )
}

private fun MainViewModel.TestResult.config(): ServerConfig = when (this) {
    is MainViewModel.TestResult.Success -> config
    is MainViewModel.TestResult.Untrusted -> config
    is MainViewModel.TestResult.Failure -> config
}

@Composable
private fun UntrustedCertCard(cert: CertInfo, pinChanged: Boolean, onTrust: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    SectionCard {
        Text(
            stringResource(if (pinChanged) R.string.cert_changed_title else R.string.cert_untrusted_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (pinChanged) StatusColors.critical else StatusColors.warning,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (pinChanged) R.string.cert_changed_text else R.string.cert_untrusted_text),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        CertLine(stringResource(R.string.cert_subject), cert.subject)
        CertLine(stringResource(R.string.cert_valid_until), dateFormat.format(cert.validUntil))
        CertLine(stringResource(R.string.cert_fingerprint), cert.fingerprint, mono = true)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onTrust) { Text(stringResource(R.string.cert_trust)) }
    }
}

@Composable
private fun TrustedCertRow(pin: String, onForget: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cert_trusted), style = MaterialTheme.typography.labelLarge)
                Text(
                    pin.take(23) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onForget) { Text(stringResource(R.string.cert_forget)) }
        }
    }
}

@Composable
private fun CertLine(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}
