package com.kidlearn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A light "grown-ups only" check (a multiplication young children can't easily answer) in
 * front of settings, downloads and course management. Not a security boundary.
 */
@Composable
fun GrownUpGate(onPassed: () -> Unit, onCancel: () -> Unit) {
    var a by rememberSaveable { mutableIntStateOf(Random.nextInt(6, 10)) }
    var b by rememberSaveable { mutableIntStateOf(Random.nextInt(6, 10)) }
    var answer by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Grown-ups only 🔒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What is $a × $b?", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it.filter(Char::isDigit).take(3); wrong = false },
                    singleLine = true,
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wrong) Text("Not quite. Try this one.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (answer.toIntOrNull() == a * b) {
                    onPassed()
                } else {
                    wrong = true
                    answer = ""
                    a = Random.nextInt(6, 10)
                    b = Random.nextInt(6, 10)
                }
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
