package com.kidlearn.data.repository

import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.repository.Clock
import com.kidlearn.domain.repository.QuestionRepository
import com.kidlearn.sync.QuestionLogCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps every question in one human-readable JSON file (`files/questions/questions.json`).
 * Writes go to a temp file first and are then renamed, so the log is never half-written.
 */
internal class FileQuestionRepository(
    private val logFile: File,
    private val shareDir: File,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) : QuestionRepository {
    private val mutex = Mutex()
    private val questions = MutableStateFlow<List<KidQuestion>?>(null)

    override fun observeQuestions(): Flow<List<KidQuestion>> =
        questions.onStart { mutex.withLock { ensureLoaded() } }.filterNotNull()

    override suspend fun add(question: KidQuestion) = update { it + question }

    override suspend fun markUploaded(ids: Set<String>, atMillis: Long) = update { list ->
        list.map { if (it.id in ids && it.uploadedAtMillis == null) it.copy(uploadedAtMillis = atMillis) else it }
    }

    override suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    override suspend fun exportForSharing(deviceId: String, deviceName: String): File = mutex.withLock {
        val all = ensureLoaded()
        withContext(io) {
            shareDir.mkdirs()
            shareDir.listFiles()?.forEach { it.delete() } // only keep the latest export
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(clock.nowMillis()))
            File(shareDir, "kidlearn-questions-$stamp.json").apply {
                writeText(QuestionLogCodec.encode(all, deviceId, deviceName, clock.nowMillis()))
            }
        }
    }

    private suspend fun update(transform: (List<KidQuestion>) -> List<KidQuestion>) = mutex.withLock {
        val updated = transform(ensureLoaded())
        withContext(io) {
            logFile.parentFile?.mkdirs()
            val temp = File(logFile.parentFile, "${logFile.name}.tmp")
            temp.writeText(QuestionLogCodec.encode(updated, deviceId = "", deviceName = "", exportedAtMillis = clock.nowMillis()))
            if (!temp.renameTo(logFile)) {
                logFile.delete()
                temp.renameTo(logFile)
            }
        }
        questions.value = updated
    }

    /** Must be called with [mutex] held. */
    private suspend fun ensureLoaded(): List<KidQuestion> {
        questions.value?.let { return it }
        val loaded = withContext(io) {
            runCatching { if (logFile.isFile) QuestionLogCodec.decode(logFile.readText()) else emptyList() }.getOrDefault(emptyList())
        }
        questions.value = loaded
        return loaded
    }
}
