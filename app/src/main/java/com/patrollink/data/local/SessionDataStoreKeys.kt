package com.patrollink.data.local

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SessionDataStoreKeys {
    val AccessToken = stringPreferencesKey("access_token")
    val RefreshToken = stringPreferencesKey("refresh_token")
    val ExpiresInSeconds = longPreferencesKey("expires_in_seconds")
}
