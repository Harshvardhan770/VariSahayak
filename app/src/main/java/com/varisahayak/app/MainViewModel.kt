package com.varisahayak.app

import androidx.lifecycle.ViewModel
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val authState = authRepository.authState
    val profile = profileRepository.observeCurrentProfile()
}
