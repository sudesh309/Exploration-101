package com.kidlearn.feature.questions

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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.EmptyState
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The learner's questions, plus sharing: "Share" hands the JSON log to any app (email, chat,
 * Google Drive...), "Upload" commits it to the configured GitHub repository.
 */
@Composable
fun QuestionsScreen(viewModel: QuestionsViewModel, onBack: () -> Unit, onShareFile: (File) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(viewModel) { viewModel.shareFile.collect { onShareFile(it) } }

    Scaffold(topBar = { KidTopBar("My questions 🙋", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show everyone's questions", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.showAllLearners, onCheckedChange = viewModel::setShowAllLearners)
                    }
                    BigButton("📤 Share questions file", onClick = viewModel::share, enabled = !state.busy)
                    OutlinedButton(onClick = viewModel::upload, enabled = !state.busy && state.canUpload, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.canUpload) "☁️ Upload to ${state.uploadTarget}" else "☁️ Upload (set up GitHub in grown-ups settings)")
                    }
                    Text("${state.pendingUpload} not uploaded yet", style = MaterialTheme.typography.bodyMedium)
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (state.questions.isEmpty()) {
                item(key = "empty") {
                    EmptyState("🤔", "No questions yet. Tap \"Ask a question\" under anything in a lesson!", Modifier.padding(top = 32.dp))
                }
            }
            items(state.questions, key = { it.id }) { question -> QuestionCard(question, onDelete = { viewModel.delete(question) }) }
        }
    }
}

@Composable
private fun QuestionCard(question: KidQuestion, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("“${question.text}”", style = MaterialTheme.typography.titleMedium)
                Text("${question.courseTitle} › ${question.lessonTitle}", style = MaterialTheme.typography.bodyMedium)
                question.blockPreview?.let {
                    Text("About: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${question.profileName} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(question.askedAtMillis))}" +
                        if (question.isUploaded) " • ☁️ uploaded" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete question") }
        }
    }
}
