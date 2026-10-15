package com.kidlearn.feature.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidlearn.domain.model.Profile
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.KidTopBar
import com.kidlearn.ui.components.LoadingBox

@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    onSelect: (Profile) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Profile) -> Unit,
) {
    val profiles by viewModel.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Profile?>(null) }

    Scaffold(topBar = { KidTopBar("Who is learning today?") }) { padding ->
        val list = profiles
        if (list == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { profile ->
                ProfileCard(profile, onClick = { onSelect(profile) }, onEdit = { onEdit(profile) }, onDelete = { toDelete = profile })
            }
            item(key = "add") {
                Card(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onAdd),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("➕", style = MaterialTheme.typography.headlineLarge)
                        Text("New learner", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    toDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("All progress and marks for ${profile.name} will be removed.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(profile); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(profile.avatar, style = MaterialTheme.typography.headlineLarge)
            Text(profile.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Age ${profile.age}", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.name}") }
            }
        }
    }
}

@Composable
fun EditProfileScreen(viewModel: EditProfileViewModel, isNew: Boolean, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    Scaffold(topBar = { KidTopBar(if (isNew) "New learner" else "Edit learner", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { name -> viewModel.update { it.copy(name = name.take(30)) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Age: ${form.age}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = form.age.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(age = v.toInt()) } },
                valueRange = ProfileForm.MIN_AGE.toFloat()..ProfileForm.MAX_AGE.toFloat(),
                steps = ProfileForm.MAX_AGE - ProfileForm.MIN_AGE - 1,
            )
            Text("Pick an avatar", style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(GridCells.Adaptive(64.dp), modifier = Modifier.weight(1f)) {
                items(ProfileForm.AVATARS) { avatar ->
                    val selected = avatar == form.avatar
                    Card(
                        Modifier.padding(4.dp).size(64.dp).clip(MaterialTheme.shapes.medium).clickable { viewModel.update { it.copy(avatar = avatar) } },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(avatar, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            BigButton("Save", onClick = { viewModel.save(onSaved) }, enabled = form.isValid)
        }
    }
}
