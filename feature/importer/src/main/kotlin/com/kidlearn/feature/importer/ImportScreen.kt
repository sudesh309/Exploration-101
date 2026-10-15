package com.kidlearn.feature.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.KidTopBar

/** For parents/teachers: add or update course packs from a file, a web link, or the import folder. */
@Composable
fun ImportScreen(viewModel: ImportViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<CourseSummary?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }

    Scaffold(topBar = { KidTopBar("Add & update courses", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "A course pack is a .zip with a course.json file plus pictures, sounds and videos. " +
                            "Importing a newer version of a course updates it and keeps every learner's progress.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    BigButton(
                        "📁 Choose a pack file",
                        enabled = !status.busy,
                        onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream", "text/plain")) },
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("…or paste a download link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { viewModel.importUrl(url) }, enabled = !status.busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("⬇️ Download and add")
                    }
                    OutlinedButton(onClick = viewModel::scanImportFolder, enabled = !status.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("🔎 Scan import folder")
                    }
                    Text("Import folder (copy packs here over USB): ${viewModel.importFolderPath}", style = MaterialTheme.typography.bodyMedium)
                    if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    status.messages.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            item(key = "installed-title") {
                Text("Installed courses (${installed.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            }
            items(installed, key = { it.id }) { course ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(course.title, style = MaterialTheme.typography.titleMedium)
                            Text("${course.subject.name} • v${course.version} • ${course.lessonCount} lessons", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { toRemove = course }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${course.title}") }
                    }
                }
            }
        }
    }

    toRemove?.let { course ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove \"${course.title}\"?") },
            text = { Text("Its files are deleted from this phone. Learners' marks are kept and come back if you import it again.") },
            confirmButton = { TextButton(onClick = { viewModel.remove(course.id); toRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}
