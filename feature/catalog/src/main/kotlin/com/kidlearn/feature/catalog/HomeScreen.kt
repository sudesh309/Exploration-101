package com.kidlearn.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.ui.components.EmptyState
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox
import com.kidlearn.ui.components.MediaImage
import com.kidlearn.ui.theme.subjectColor

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenCourse: (courseId: String) -> Unit,
    onOpenResults: () -> Unit,
    onOpenImport: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            KidTopBar(
                title = state.profile?.let { "Hi ${it.avatar} ${it.name}!" } ?: "KidLearn",
                actions = {
                    IconButton(onClick = onOpenResults) { Icon(Icons.Filled.Star, contentDescription = "My results") }
                    IconButton(onClick = onOpenImport) { Icon(Icons.Filled.Add, contentDescription = "Add courses") }
                    TextButton(onClick = onSwitchProfile) { Text("Switch") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "subjects") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(state.selectedSubjectId == null, { viewModel.selectSubject(null) }, { Text("All") }) }
                    items(state.subjects, key = { it.id }) { subject ->
                        FilterChip(
                            selected = state.selectedSubjectId == subject.id,
                            onClick = { viewModel.selectSubject(subject.id) },
                            label = { Text(subject.name) },
                        )
                    }
                }
            }
            if (state.hiddenByAge > 0 || state.showAllAges) {
                item(key = "ages") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show courses for all ages", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.showAllAges, onCheckedChange = viewModel::setShowAllAges)
                    }
                }
            }
            if (state.courses.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp)) {
                        EmptyState("📦", "No courses here yet. Tap + to add a course pack.")
                    }
                }
            }
            items(state.courses, key = { it.id }) { course -> CourseCard(course) { onOpenCourse(course.id) } }
        }
    }
}

@Composable
private fun CourseCard(course: CourseSummary, onClick: () -> Unit) {
    val color = subjectColor(course.subject.colorHex, MaterialTheme.colorScheme.primary)
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                val cover = course.coverImage ?: course.subject.icon
                if (cover != null) MediaImage(cover, null, Modifier.fillMaxSize(), ContentScale.Crop)
                else Text("📘", style = MaterialTheme.typography.headlineLarge)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(course.subject.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
                Text(course.title, style = MaterialTheme.typography.titleLarge)
                Text("Ages ${course.ageRange.min}–${course.ageRange.max} • ${course.lessonCount} lessons", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
