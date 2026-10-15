package com.kidlearn.data.repository

import android.util.LruCache
import com.kidlearn.content.install.CourseStore
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.repository.CourseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Course catalogue backed by [CourseStore]. All disk work runs on [io]. Only a handful of parsed
 * courses are kept in memory (LRU), so the catalogue can grow without growing RAM use.
 */
internal class FileCourseRepository(
    private val store: CourseStore,
    private val io: CoroutineDispatcher,
) : CourseRepository {
    private val mutex = Mutex()
    private val cache = LruCache<String, Course>(MAX_CACHED_COURSES)
    private val summaries = MutableStateFlow<List<CourseSummary>?>(null)

    override fun observeCourses(): Flow<List<CourseSummary>> =
        summaries.asStateFlow().onStart { if (summaries.value == null) refresh() }.filterNotNull()

    override suspend fun getCourse(courseId: String): Course? {
        cache.get(courseId)?.let { return it }
        return withContext(io) { store.load(courseId) }?.also { cache.put(courseId, it) }
    }

    override suspend fun install(source: CoursePackSource, allowSameVersion: Boolean): InstallOutcome = mutex.withLock {
        val outcome = withContext(io) { store.install(source, allowSameVersion) }
        when (outcome) {
            is InstallOutcome.Installed -> invalidate(outcome.course.id)
            is InstallOutcome.Updated -> invalidate(outcome.course.id)
            else -> Unit
        }
        outcome
    }

    override suspend fun remove(courseId: String) = mutex.withLock {
        withContext(io) { store.remove(courseId) }
        invalidate(courseId)
    }

    private suspend fun invalidate(courseId: String) {
        cache.remove(courseId)
        refresh()
    }

    private suspend fun refresh() {
        summaries.value = withContext(io) {
            store.installedCourseIds().mapNotNull { id -> store.load(id)?.toSummary() }
        }
    }

    private companion object {
        const val MAX_CACHED_COURSES = 4
    }
}
