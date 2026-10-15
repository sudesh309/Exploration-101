package com.kidlearn.feature.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kidlearn.domain.model.ChoiceQuestion
import com.kidlearn.domain.model.ChoiceResponse
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.NumberQuestion
import com.kidlearn.domain.model.Question
import com.kidlearn.domain.model.QuestionResponse
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.domain.model.QuizResult
import com.kidlearn.domain.model.TextResponse
import com.kidlearn.ui.blocks.BlockRenderer
import com.kidlearn.ui.blocks.LessonSession
import com.kidlearn.ui.components.BigButton
import com.kidlearn.ui.components.MediaImage
import com.kidlearn.ui.components.StarRow
import kotlinx.coroutines.launch

/** Renderers for the quiz learning mode. */
fun quizBlockRenderers(): List<BlockRenderer> = listOf(QuizBlockRenderer)

/**
 * One question at a time (less overwhelming for young children), then a result card with
 * stars and explanations. Each submission is stored as an attempt; the report uses the best.
 */
private object QuizBlockRenderer : BlockRenderer {
    override val type = QuizBlock.TYPE

    @Composable
    override fun Render(block: ContentBlock, session: LessonSession) {
        val quiz = (block as QuizBlock).quiz
        val scope = rememberCoroutineScope()
        var index by rememberSaveable(quiz.id) { mutableIntStateOf(0) }
        val responses = remember(quiz.id) { mutableStateMapOf<String, QuestionResponse>() }
        var result by remember(quiz.id) { mutableStateOf<QuizResult?>(null) }
        var submitting by remember { mutableStateOf(false) }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("❓ ${quiz.title ?: "Quiz time!"}", style = MaterialTheme.typography.titleLarge)
                val finished = result
                if (finished != null) {
                    QuizResultView(finished, quiz.questions) {
                        responses.clear()
                        result = null
                        index = 0
                    }
                    return@Column
                }
                val question = quiz.questions[index.coerceIn(quiz.questions.indices)]
                LinearProgressIndicator(progress = { (index + 1f) / quiz.questions.size }, modifier = Modifier.fillMaxWidth())
                Text("Question ${index + 1} of ${quiz.questions.size}", style = MaterialTheme.typography.bodyMedium)
                QuestionView(question, responses[question.id]) { responses[question.id] = it }

                val isLast = index == quiz.questions.lastIndex
                BigButton(
                    text = if (isLast) "Check my answers" else "Next ➜",
                    enabled = responses[question.id].isAnswered() && !submitting,
                    onClick = {
                        if (!isLast) {
                            index++
                        } else {
                            submitting = true
                            scope.launch {
                                result = session.submitQuiz(quiz, responses.toMap())
                                submitting = false
                            }
                        }
                    },
                )
                if (index > 0) OutlinedButton(onClick = { index-- }, modifier = Modifier.fillMaxWidth()) { Text("⬅ Back") }
            }
        }
    }

    private fun QuestionResponse?.isAnswered(): Boolean = when (this) {
        is ChoiceResponse -> selected.isNotEmpty()
        is TextResponse -> value.isNotBlank()
        null -> false
        else -> true
    }
}

@Composable
private fun QuestionView(question: Question, response: QuestionResponse?, onAnswer: (QuestionResponse) -> Unit) {
    Text(question.prompt, style = MaterialTheme.typography.titleMedium)
    question.image?.let {
        MediaImage(it, null, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(MaterialTheme.shapes.medium))
    }
    when (question) {
        is ChoiceQuestion -> ChoiceOptions(question, (response as? ChoiceResponse)?.selected.orEmpty(), onAnswer)
        is NumberQuestion -> TypedAnswer((response as? TextResponse)?.value.orEmpty(), KeyboardType.Decimal, onAnswer)
        else -> TypedAnswer((response as? TextResponse)?.value.orEmpty(), KeyboardType.Text, onAnswer)
    }
}

@Composable
private fun ChoiceOptions(question: ChoiceQuestion, selected: Set<Int>, onAnswer: (QuestionResponse) -> Unit) {
    if (question.multiSelect) Text("Pick all the right answers", style = MaterialTheme.typography.bodyMedium)
    question.options.forEachIndexed { i, option ->
        val isSelected = i in selected
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).clickable {
                val next = when {
                    !question.multiSelect -> setOf(i)
                    isSelected -> selected - i
                    else -> selected + i
                }
                onAnswer(ChoiceResponse(next))
            },
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isSelected) "✅  " else "⚪  ")
                Text(option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TypedAnswer(value: String, keyboard: KeyboardType, onAnswer: (QuestionResponse) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onAnswer(TextResponse(it.take(100))) },
        singleLine = true,
        label = { Text("Your answer") },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QuizResultView(result: QuizResult, questions: List<Question>, onRetry: () -> Unit) {
    val stars = when {
        result.percent >= 90 -> 3
        result.percent >= 70 -> 2
        result.percent >= 40 -> 1
        else -> 0
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StarRow(stars, size = 44.dp)
        Text("${result.score} / ${result.maxScore} points (${result.percent}%)", style = MaterialTheme.typography.headlineSmall)
        Text(if (result.passed) "Great job! 🎉" else "Good try! Let's practise again 💪", style = MaterialTheme.typography.titleMedium)
    }
    questions.forEach { q ->
        val correct = result.correctness[q.id] == true
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${if (correct) "✅" else "❌"} ${q.prompt}", style = MaterialTheme.typography.bodyLarge)
            if (!correct) answerText(q)?.let { Text("Answer: $it", style = MaterialTheme.typography.bodyMedium) }
            q.explanation?.let { Text("💡 $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("🔁 Try again") }
}

private fun answerText(question: Question): String? = when (question) {
    is ChoiceQuestion -> question.correctOptions.sorted().joinToString { question.options[it] }
    is NumberQuestion -> question.answer.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
    is com.kidlearn.domain.model.TextQuestion -> question.acceptedAnswers.firstOrNull()
    else -> null
}
