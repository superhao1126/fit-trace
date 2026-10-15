package com.fittrace.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.model.AppResult
import com.fittrace.core.model.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppViewModel(private val auth: AuthRepository) : ViewModel() {
    val sessionStatus = auth.sessionStatus
    private val authStore = ViewModelStore()
    val authFlowOwner: ViewModelStoreOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = authStore
        }

    init {
        viewModelScope.launch {
            sessionStatus.collect { if (it is SessionStatus.SignedIn) authStore.clear() }
        }
    }

    override fun onCleared() {
        authStore.clear()
    }

    private var restoreJob: Job? = null
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun restore() {
        if (restoreJob?.isActive == true) return
        restoreJob =
            viewModelScope.launch {
                mutableBusy.value = true
                try {
                    val result = auth.restoreSession()
                    if (
                        result is AppResult.Success && sessionStatus.value is SessionStatus.SignedIn
                    )
                        auth.currentUser()
                } finally {
                    mutableBusy.value = false
                }
            }
    }

    fun logout() {
        if (mutableBusy.value) return
        viewModelScope.launch {
            mutableBusy.value = true
            try {
                auth.logout()
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
