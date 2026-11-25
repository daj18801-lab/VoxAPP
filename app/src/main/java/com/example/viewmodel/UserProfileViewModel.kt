package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DirectoryEntry
import com.example.data.ProfilePost
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserFollow
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserProfileViewModel(
    private val repository: VoxStageRepository,
    val currentUserId: String,
    val targetUserId: String
) : ViewModel() {

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _isUpdatingFollow = MutableStateFlow(false)
    val isUpdatingFollow: StateFlow<Boolean> = _isUpdatingFollow.asStateFlow()

    val directoryEntryState: StateFlow<UiState<DirectoryEntry?>> =
        repository.observeDirectoryEntry(targetUserId)
            .map<DirectoryEntry?, UiState<DirectoryEntry?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing user profile $targetUserId", root)
                emit(UiState.Error(root.message ?: "Could not load user profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val postsState: StateFlow<UiState<List<ProfilePost>>> =
        repository.observePostsByUser(targetUserId)
            .map<List<ProfilePost>, UiState<List<ProfilePost>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing posts for $targetUserId", root)
                emit(UiState.Error(root.message ?: "Could not load posts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val followersState: StateFlow<List<UserFollow>> =
        repository.observeFollowers(targetUserId)
            .catch { error ->
                Log.w(TAG, "Error observing followers for $targetUserId", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val followingState: StateFlow<List<UserFollow>> =
        repository.observeFollowing(targetUserId)
            .catch { error ->
                Log.w(TAG, "Error observing following for $targetUserId", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    fun toggleFollow(currentlyFollowing: Boolean) {
        if (currentUserId == targetUserId || _isUpdatingFollow.value) return
        viewModelScope.launch {
            _isUpdatingFollow.value = true
            val result = if (currentlyFollowing) {
                repository.unfollowUser(targetUserId)
            } else {
                repository.followUser(targetUserId)
            }
            result
                .onSuccess {
                    _snackbarEvents.tryEmit(if (currentlyFollowing) "Unfollowed" else "Following!")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update follow status")
                }
            _isUpdatingFollow.value = false
        }
    }

    fun toggleLikePost(post: ProfilePost) {
        viewModelScope.launch {
            repository.toggleLikeProfilePost(post)
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update like")
                }
        }
    }

    fun deletePost(post: ProfilePost) {
        viewModelScope.launch {
            repository.deleteProfilePost(post)
                .onSuccess { _snackbarEvents.tryEmit("Post deleted") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not delete post") }
        }
    }

    private var hasRecordedView = false

    fun recordProfileViewIfNeeded(viewerProfile: UserProfile?, viewerTier: StaffTier) {
        if (hasRecordedView || viewerProfile == null || currentUserId == targetUserId) return
        hasRecordedView = true
        viewModelScope.launch {
            repository.recordProfileView(
                targetUserId = targetUserId,
                viewerProfile = viewerProfile,
                viewerTier = viewerTier
            )
        }
    }

    private companion object {
        const val TAG = "UserProfileViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
