package dev.killer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.killer.engine.KillerDiagnosticData
import dev.killer.ui.theme.KillerColors

/** The same signal diagnostics as TRMX, with no network or inferred live state. */
@Composable
fun DiagnosticsScreen(data: KillerDiagnosticData?, onRefresh: () -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(KillerColors.Background).padding(horizontal = 20.dp)) {
        Row { TextButton(onClick = onBack) { Text("Back") }; TextButton(onClick = onRefresh) { Text("Refresh") } }
        Text("App detection diagnostics", color = KillerColors.TextPrimary)
        if (data == null) {
            Text("Loading…", color = KillerColors.TextSecondary)
        } else {
            Text("Usage access: ${data.isUsageAccessGranted} • Accessibility: ${data.isAccessibilityEnabled}", color = KillerColors.TextSecondary)
            Text("Installed: ${data.totalInstalledApps} • Usage: ${data.usageStatsCount} • Observed: ${data.accessibilityObservedCount} • Activity manager: ${data.activityManagerFallbackCount} • Detected: ${data.runningCount}", color = KillerColors.TextSecondary)
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search apps or packages") }, modifier = Modifier.fillMaxWidth())
            LazyColumn {
                items(data.apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(app.label, color = KillerColors.TextPrimary)
                        Text("${app.packageName} • ${if (app.isSystem) "System" else "User"} • ${if (app.inRunning) "Detected" else app.notRunningReason ?: "Not detected"}", color = KillerColors.TextSecondary)
                        Text("Last used: ${app.lastTimeUsedFormatted} • Observed: ${app.isAccessibilityObserved} • Stopped: ${app.isStopped}", color = KillerColors.TextSecondary)
                    }
                }
            }
        }
    }
}
