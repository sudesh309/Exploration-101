package com.kidlearn.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox

/** Grown-ups area: online sources (GitHub, Google Drive), download, and links to management screens. */
@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit,
    onManageCourses: () -> Unit,
    onOpenQuestions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { KidTopBar("Grown-ups: courses & sharing", onBack = onBack) }) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val form = state.form
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "download") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Download courses", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Checks every source set up below and installs new or updated courses. Learning stays offline; " +
                                "only this download needs the internet.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        BigButton("⬇️ Download now", onClick = { viewModel.download(force = false) }, enabled = !state.downloading)
                        TextButton(onClick = { viewModel.download(force = true) }, enabled = !state.downloading) {
                            Text("Download everything again")
                        }
                        if (state.downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.report.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item(key = "github") {
                Section("GitHub repository") {
                    Text(
                        "Courses: folders with a course.json and/or .zip packs anywhere in the repository (or in the folder below). " +
                            "A token is needed for private repositories and to upload questions.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Field("Owner (user or organisation)", form.github.owner) { v -> viewModel.edit { it.copy(github = it.github.copy(owner = v)) } }
                    Field("Repository name", form.github.repo) { v -> viewModel.edit { it.copy(github = it.github.copy(repo = v)) } }
                    Field("Branch", form.github.branch) { v -> viewModel.edit { it.copy(github = it.github.copy(branch = v)) } }
                    Field("Courses folder (optional)", form.github.contentPath) { v ->
                        viewModel.edit { it.copy(github = it.github.copy(contentPath = v)) }
                    }
                    Field("Questions upload folder", form.github.questionsPath) { v ->
                        viewModel.edit { it.copy(github = it.github.copy(questionsPath = v)) }
                    }
                    Field("Access token (optional)", form.github.token, secret = true) { v ->
                        viewModel.edit { it.copy(github = it.github.copy(token = v)) }
                    }
                }
            }
            item(key = "drive") {
                Section("Google Drive") {
                    Text(
                        "Paste the link of a shared .zip file (\"Anyone with the link\"), or of a shared folder. " +
                            "Folders also need a Google API key with the Drive API enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Field("File or folder link", form.drive.link) { v -> viewModel.edit { it.copy(drive = it.drive.copy(link = v)) } }
                    Field("API key (for folders)", form.drive.apiKey, secret = true) { v ->
                        viewModel.edit { it.copy(drive = it.drive.copy(apiKey = v)) }
                    }
                }
            }
            item(key = "device") {
                Section("This device") {
                    Field("Device name (shown in uploaded questions)", form.deviceName) { v -> viewModel.edit { it.copy(deviceName = v) } }
                }
            }
            item(key = "save") {
                BigButton(if (state.dirty) "💾 Save settings" else "Settings saved", onClick = viewModel::save, enabled = state.dirty)
            }
            item(key = "links") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    OutlinedButton(onClick = onOpenQuestions, modifier = Modifier.fillMaxWidth()) { Text("🙋 Kids' questions: share or upload") }
                    OutlinedButton(onClick = onManageCourses, modifier = Modifier.fillMaxWidth()) { Text("📦 Add from file / link, remove courses") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}
