package com.kidlearn.data

import android.content.Context
import com.kidlearn.content.install.CourseStore
import com.kidlearn.data.db.KidLearnDatabase
import com.kidlearn.data.repository.FileCourseRepository
import com.kidlearn.data.repository.RoomProfileRepository
import com.kidlearn.data.repository.RoomProgressRepository
import com.kidlearn.data.source.PackSources
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.repository.Clock
import com.kidlearn.domain.repository.CourseRepository
import com.kidlearn.domain.repository.ProfileRepository
import com.kidlearn.domain.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Entry point of the data layer. Everything is created lazily so app start-up stays fast and
 * nothing is allocated before a screen actually needs it.
 */
class DataModule(context: Context, private val clock: Clock = Clock.SYSTEM) {
    private val appContext = context.applicationContext
    private val io = Dispatchers.IO
    private val database by lazy { KidLearnDatabase.create(appContext) }

    val courses: CourseRepository by lazy { FileCourseRepository(CourseStore(File(appContext.filesDir, "courses")), io) }
    val profiles: ProfileRepository by lazy { RoomProfileRepository(database.profiles(), clock) }
    val progress: ProgressRepository by lazy { RoomProgressRepository(database.progress()) }
    val packSources: PackSources by lazy { PackSources(appContext, io) }

    /**
     * Installs the packs shipped inside the APK. This only runs when the app itself was
     * installed or updated (tracked by [appVersion]), so normal launches skip the unzip work.
     * Newer bundled versions update older installs; learner progress is never touched.
     */
    suspend fun syncBundledPacks(appVersion: Long): List<InstallOutcome> {
        val prefs = appContext.getSharedPreferences("content-sync", Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_BUNDLED_VERSION, -1) == appVersion) return emptyList()
        val outcomes = packSources.bundled().map { courses.install(it) }
        prefs.edit().putLong(KEY_BUNDLED_VERSION, appVersion).apply()
        return outcomes
    }

    /** Installs packs dropped into [PackSources.importFolder]; installed files are removed. */
    suspend fun importFromFolder(): List<InstallOutcome> =
        packSources.pendingImports().map { (file, source) ->
            courses.install(source).also { if (it !is InstallOutcome.Rejected) file.delete() }
        }

    private companion object {
        const val KEY_BUNDLED_VERSION = "bundled-version"
    }
}
