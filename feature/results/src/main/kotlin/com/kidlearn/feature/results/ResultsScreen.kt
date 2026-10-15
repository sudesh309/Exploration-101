package com.kidlearn.feature.results

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.CourseReport
import com.kidlearn.domain.model.Marks
import com.kidlearn.domain.model.ProfileReport
import com.kidlearn.ui.components.EmptyState
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox
import com.kidlearn.ui.components.StarRow
import com.kidlearn.ui.theme.subjectColor

@Composable
fun ResultsScreen(viewModel: ResultsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { KidTopBar("My results", onBack = onBack) }) { padding ->
        when (val s = state) {
            ResultsUiState.Loading -> LoadingBox(Modifier.padding(padding))
            ResultsUiState.NoProfile -> EmptyState("🙈", "Pick a learner first.", Modifier.padding(padding))
            is ResultsUiState.Ready -> Report(s.report, padding)
        }
    }
}

@Composable
private fun Report(report: ProfileReport, padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "overall") { OverallCard(report) }
        report.subjects.forEach { subject ->
            item(key = "subject-${subject.subject.id}") {
                val color = subjectColor(subject.subject.colorHex, MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(subject.subject.name, style = MaterialTheme.typography.headlineSmall, color = color, modifier = Modifier.weight(1f))
                    MarksText(subject.totalMarks)
                }
            }
            items(subject.courses, key = { "course-${it.course.id}" }) { CourseReportCard(it) }
        }
    }
}

@Composable
private fun OverallCard(report: ProfileReport) {
    val total = report.totalMarks
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${report.profile.avatar} ${report.profile.name}", style = MaterialTheme.typography.headlineSmall)
            StarRow(total.grade.stars, size = 40.dp)
            Text("Grade ${total.grade.label}", style = MaterialTheme.typography.headlineLarge)
            Text("${total.earned} / ${total.possible} marks (${total.percent}%)", style = MaterialTheme.typography.titleMedium)
            Text("${report.lessonsCompleted} lessons completed", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CourseReportCard(report: CourseReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(report.course.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(report.totalMarks.grade.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text("Lessons: ${report.lessonsCompleted}/${report.lessonsTotal}", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { report.completionPercent / 100f }, modifier = Modifier.fillMaxWidth())
            Row {
                Text("Learning marks", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                MarksText(report.learningMarks)
            }
            Row {
                Text("Quiz marks", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                MarksText(report.quizMarks)
            }
            if (report.quizzes.isNotEmpty()) {
                HorizontalDivider()
                report.quizzes.forEach { quiz ->
                    Row {
                        Text("❓ ${quiz.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (quiz.best == null) "not taken" else "best ${quiz.best}/${quiz.max} • ${quiz.attempts} tries",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarksText(marks: Marks) {
    Text("${marks.earned}/${marks.possible} (${marks.percent}%)", style = MaterialTheme.typography.titleMedium)
}
