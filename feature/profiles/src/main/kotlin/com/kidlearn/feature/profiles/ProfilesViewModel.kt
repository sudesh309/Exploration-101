package com.kidlearn.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(private val profiles: ProfileRepository) : ViewModel() {
    /** null while loading. */
    val state: StateFlow<List<Profile>?> = profiles.observeProfiles()
        .map<List<Profile>, List<Profile>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(profile: Profile) {
        viewModelScope.launch { profiles.deleteProfile(profile.id) }
    }
}

data class ProfileForm(
    val name: String = "",
    val age: Int = 7,
    val avatar: String = AVATARS.first(),
) {
    val isValid: Boolean get() = name.isNotBlank() && age in MIN_AGE..MAX_AGE

    companion object {
        const val MIN_AGE = 5
        const val MAX_AGE = 15
        val AVATARS = listOf("🦁", "🐼", "🦊", "🐸", "🐙", "🦄", "🐯", "🐧", "🐨", "🚀", "🤖", "🦖")
    }
}

class EditProfileViewModel(
    private val profiles: ProfileRepository,
    private val profileId: Long?,
) : ViewModel() {
    private val _form = MutableStateFlow(ProfileForm())
    val form: StateFlow<ProfileForm> = _form.asStateFlow()
    private var existing: Profile? = null

    init {
        if (profileId != null) {
            viewModelScope.launch {
                existing = profiles.getProfile(profileId)?.also { _form.value = ProfileForm(it.name, it.age, it.avatar) }
            }
        }
    }

    fun update(transform: (ProfileForm) -> ProfileForm) {
        _form.value = transform(_form.value)
    }

    fun save(onSaved: (profileId: Long) -> Unit) {
        val f = _form.value
        if (!f.isValid) return
        viewModelScope.launch {
            val current = existing
            val id = if (current != null) {
                profiles.updateProfile(current.copy(name = f.name, age = f.age, avatar = f.avatar))
                current.id
            } else {
                profiles.createProfile(f.name, f.age, f.avatar).id
            }
            onSaved(id)
        }
    }
}
