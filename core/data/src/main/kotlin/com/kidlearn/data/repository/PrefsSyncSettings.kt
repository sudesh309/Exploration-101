package com.kidlearn.data.repository

import android.content.Context
import com.kidlearn.domain.model.DriveSource
import com.kidlearn.domain.model.GitHubSource
import com.kidlearn.domain.model.SyncSettings
import com.kidlearn.domain.repository.SyncSettingsRepository
import com.kidlearn.sync.SyncStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Sync settings in app-private SharedPreferences (not readable by other apps; excluded from
 * backups, see backup_rules.xml). Use a fine-grained GitHub token limited to one repository.
 */
internal class PrefsSyncSettingsRepository(context: Context) : SyncSettingsRepository, SyncStateStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override fun observeSettings(): Flow<SyncSettings> = state.asStateFlow()

    override suspend fun current(): SyncSettings = state.value

    override suspend fun save(settings: SyncSettings) {
        prefs.edit()
            .putString("gh_owner", settings.github.owner.trim())
            .putString("gh_repo", settings.github.repo.trim())
            .putString("gh_branch", settings.github.branch.trim().ifEmpty { "main" })
            .putString("gh_path", settings.github.contentPath.trim())
            .putString("gh_questions", settings.github.questionsPath.trim().ifEmpty { "questions" })
            .putString("gh_token", settings.github.token.trim())
            .putString("drive_link", settings.drive.link.trim())
            .putString("drive_key", settings.drive.apiKey.trim())
            .putString("device_name", settings.deviceName.trim())
            .apply()
        state.value = read()
    }

    override fun deviceId(): String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    override fun get(key: String): String? = prefs.getString("sync_state:$key", null)

    override fun put(key: String, value: String) {
        prefs.edit().putString("sync_state:$key", value).apply()
    }

    private fun read() = SyncSettings(
        github = GitHubSource(
            owner = prefs.getString("gh_owner", "").orEmpty(),
            repo = prefs.getString("gh_repo", "").orEmpty(),
            branch = prefs.getString("gh_branch", "main").orEmpty(),
            contentPath = prefs.getString("gh_path", "").orEmpty(),
            questionsPath = prefs.getString("gh_questions", "questions").orEmpty(),
            token = prefs.getString("gh_token", "").orEmpty(),
        ),
        drive = DriveSource(
            link = prefs.getString("drive_link", "").orEmpty(),
            apiKey = prefs.getString("drive_key", "").orEmpty(),
        ),
        deviceName = prefs.getString("device_name", "").orEmpty(),
    )

    private companion object {
        const val PREFS = "sync-settings"
    }
}
