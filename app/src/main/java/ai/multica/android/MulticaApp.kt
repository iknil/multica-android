package ai.multica.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.multica.android.ui.theme.MulticaTheme

@Composable
fun MulticaApp() {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Multica", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(28.dp))
                Text(
                    "Native Android feasibility",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "A compiled Kotlin and Compose boundary for service integration.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            HorizontalDivider()
            ContractRow("Build route", "Kotlin + Compose", StatusColor.Ready)
            ContractRow("HTTP identity", "mobile / android", StatusColor.Ready)
            ContractRow("Realtime", "auth-first / foreground", StatusColor.Ready)
            ContractRow("Environment URL", "Awaiting verified value", StatusColor.Pending)
            ContractRow("Signing identity", "Awaiting release owner", StatusColor.Pending)
            Spacer(Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.surface) {
                Text(
                    "No production request is sent by this feasibility build.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private enum class StatusColor { Ready, Pending }

@Composable
private fun ContractRow(label: String, value: String, status: StatusColor) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (status == StatusColor.Ready) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    CircleShape,
                ),
        )
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() = MulticaTheme { MulticaApp() }
