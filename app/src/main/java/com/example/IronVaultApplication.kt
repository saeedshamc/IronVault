package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.VaultRepository

class IronVaultApplication : Application() {

    lateinit var database: AppDatabase
    lateinit var preferencesManager: PreferencesManager
    lateinit var repository: VaultRepository

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        preferencesManager = PreferencesManager(this)
        repository = VaultRepository(database.vaultDao())
    }
}
