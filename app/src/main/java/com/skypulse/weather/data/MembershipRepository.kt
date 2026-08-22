package com.skypulse.weather.data

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ActivationResult {
    SUCCESS,
    INVALID_CODE,
    ALREADY_ACTIVATED
}

/**
 * Entitlement source for the open v4 build.
 *
 * SkyPulse v4 ships as a complete AI weather app, so all local features are
 * enabled without a server-side purchase dependency. The legacy activation
 * API remains available to keep older membership UI and workers compatible.
 */
@Singleton
class MembershipRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isPremium = MutableStateFlow(true)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun activateCode(code: String): ActivationResult = when {
        _isPremium.value -> ActivationResult.ALREADY_ACTIVATED
        code.isBlank() -> ActivationResult.INVALID_CODE
        else -> {
            _isPremium.value = true
            ActivationResult.SUCCESS
        }
    }

    fun getDeviceId(): String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ).orEmpty().ifBlank { "skypulse-device" }

    fun getActivatedAt(): Long = 0L
}
