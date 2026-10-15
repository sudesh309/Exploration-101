package com.kidlearn.feature.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.ui.blocks.LessonSession
import com.kidlearn.ui.blocks.LocalBlockRenderers
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.EmptyState
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox

@Composable
fun LessonScreen(
    viewModel: LessonViewModel,
    onBack: () -> Unit,
    onOpenLesson: (lessonId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { KidTopBar((state as? LessonUiState.Ready)?.lesson?.title ?: "", onBack = onBack) }) { padding ->
        when (val s = state) {
            LessonUiState.Loading -> LoadingBox(Modifier.padding(padding))
            LessonUiState.NotFound -> EmptyState("🔍", "This lesson is not available any more.", Modifier.padding(padding))
            is LessonUiState.Ready -> LessonContent(
                state = s,
                session = viewModel,
                padding = padding,
                onFinish = viewModel::markCompleted,
                onNext = { s.nextLessonId?.let(onOpenLesson) ?: onBack() },
            )
        }
    }
}

@Composable
private fun LessonContent(
    state: LessonUiState.Ready,
    session: LessonSession,
    padding: PaddingValues,
    onFinish: () -> Unit,
    onNext: () -> Unit,
) {
    val renderers = LocalBlockRenderers.current
    val total = state.course.lessons.size
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "progress") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Lesson ${state.position} of $total", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { state.position / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }
        // Lazy list: off-screen blocks (and their images/players) are disposed, keeping memory flat.
        itemsIndexed(state.lesson.blocks, key = { index, _ -> index }, contentType = { _, block -> block.type }) { _, block ->
            val renderer = renderers.find(block.type)
            if (renderer != null) renderer.Render(block, session) else UnsupportedBlockCard(block)
        }
        item(key = "finish") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                val quizOnly = state.lesson.blocks.all { it is QuizBlock }
                if (state.completed) {
                    Text("✅ Lesson complete! +${state.lesson.completionPoints} points", style = MaterialTheme.typography.titleMedium)
                    BigButton(if (state.nextLessonId != null) "Next lesson ➜" else "Back to course", onClick = onNext)
                } else {
                    BigButton(if (quizOnly) "I'm done with the quiz!" else "I finished this lesson! 🎉", onClick = onFinish)
                    if (state.nextLessonId != null) OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
                }
            }
        }
    }
}

@Composable
private fun UnsupportedBlockCard(block: ContentBlock) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            "🧩 This part (\"${block.type}\") needs a newer version of the app.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
