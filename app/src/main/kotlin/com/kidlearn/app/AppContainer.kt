package com.kidlearn.app

import android.content.Context
import android.net.Uri
import com.kidlearn.data.DataModule
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.usecase.CompleteLesson
import com.kidlearn.domain.usecase.SubmitQuiz
import com.kidlearn.feature.importer.PackSourceProvider
import com.kidlearn.feature.lesson.visualBlockRenderers
import com.kidlearn.feature.media.mediaBlockRenderers
import com.kidlearn.feature.quiz.quizBlockRenderers
import com.kidlearn.ui.blocks.BlockRendererRegistry

/**
 * Hand-written dependency container. A DI framework would add start-up time and memory for
 * no benefit at this size; everything here is created lazily on first use.
 *
 * To add a new kind of lesson content: create a feature module with a BlockRenderer, add a
 * BlockParser for it in :core:content, and register both - nothing else changes.
 */
class AppContainer(context: Context) {
    val data = DataModule(context)

    val courses get() = data.courses
    val profiles get() = data.profiles
    val progress get() = data.progress
    val session = SessionStore(context)

    val completeLesson by lazy { CompleteLesson(data.progress) }
    val submitQuiz by lazy { SubmitQuiz(data.progress) }

    val blockRenderers by lazy {
        BlockRendererRegistry(visualBlockRenderers() + mediaBlockRenderers() + quizBlockRenderers())
    }

    val packSourceProvider: PackSourceProvider = object : PackSourceProvider {
        override fun fromUri(uri: Uri): CoursePackSource = data.packSources.fromUri(uri)
        override suspend fun fromUrl(url: String): CoursePackSource = data.packSources.fromUrl(url)
        override fun importFolderPath(): String = data.packSources.importFolder.absolutePath
        override suspend fun importFromFolder(): List<InstallOutcome> = data.importFromFolder()
    }
}

/** Remembers which learner is using the app between launches. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var currentProfileId: Long?
        get() = prefs.getLong(KEY_PROFILE, NO_PROFILE).takeIf { it != NO_PROFILE }
        set(value) = prefs.edit().putLong(KEY_PROFILE, value ?: NO_PROFILE).apply()

    private companion object {
        const val KEY_PROFILE = "profile"
        const val NO_PROFILE = -1L
    }
}
