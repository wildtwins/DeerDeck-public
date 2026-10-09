package com.wildtwins.deerdeck

import android.app.Application
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.db.DeerDeckDatabase
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository

class DeerDeckApplication : Application() {
    val database by lazy { DeerDeckDatabase.getInstance(this) }
    val repository by lazy { DeerDeckRepository(database.dao()) }
    val settingsRepository by lazy { AppSettingsRepository(this) }
}
