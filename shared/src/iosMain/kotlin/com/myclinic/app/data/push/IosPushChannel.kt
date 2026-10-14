package com.myclinic.app.data.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iPhone push. Until Apple push is set up (it needs the Apple Developer
 * account), push is off and the bell list inside the app shows everything.
 */
class IosPushChannel : PushChannel {
    override val isConfigured: Boolean = false
    private val _received = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val received: SharedFlow<String> = _received.asSharedFlow()
    private val _openRequest = MutableStateFlow<String?>(null)
    override val openRequest: StateFlow<String?> = _openRequest.asStateFlow()
    override fun onOpenHandled() {
        _openRequest.value = null
    }
    override suspend fun currentToken(): String? = null
    override suspend fun deleteToken() = Unit
}
