package com.kidlearn.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.AudioBlock
import com.kidlearn.domain.model.ImageBlock
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.domain.model.VideoBlock
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.EmptyState
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox

@Composable
fun CourseScreen(viewModel: CourseViewModel, onBack: () -> Unit, onOpenLesson: (lessonId: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { KidTopBar((state as? CourseUiState.Ready)?.course?.title.orEmpty(), onBack = onBack) }) { padding ->
        when (val s = state) {
            CourseUiState.Loading -> LoadingBox(Modifier.padding(padding))
            CourseUiState.NotFound -> EmptyState("🔍", "This course is not installed.", Modifier.padding(padding))
            is CourseUiState.Ready -> CourseOutline(s, padding, onOpenLesson)
        }
    }
}

@Composable
private fun CourseOutline(state: CourseUiState.Ready, padding: PaddingValues, onOpenLesson: (String) -> Unit) {
    val course = state.course
    val done = state.completedLessonIds.size
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (course.description.isNotBlank()) Text(course.description, style = MaterialTheme.typography.bodyLarge)
                Text("$done of ${course.lessons.size} lessons done", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { done / course.lessons.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                state.nextLessonId?.let { BigButton(if (done == 0) "Start learning ▶" else "Continue ▶", onClick = { onOpenLesson(it) }) }
            }
        }
        course.units.forEach { unit ->
            item(key = "unit-${unit.id}") {
                Text(unit.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            }
            items(unit.lessons, key = { "lesson-${it.id}" }) { lesson ->
                LessonRow(lesson, lesson.id in state.completedLessonIds) { onOpenLesson(lesson.id) }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, completed: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (completed) "✅" else "⭕", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                lesson.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Text(modeIcons(lesson), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Shows at a glance which learning modes a lesson uses. */
private fun modeIcons(lesson: Lesson): String = buildString {
    val blocks = lesson.blocks
    if (blocks.any { it is ImageBlock }) append("🖼️")
    if (blocks.any { it is AudioBlock }) append("🎧")
    if (blocks.any { it is VideoBlock }) append("🎬")
    if (blocks.any { it is QuizBlock }) append("❓")
    if (isEmpty()) append("📖")
}
