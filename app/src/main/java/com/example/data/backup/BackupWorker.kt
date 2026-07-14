package com.example.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.security.SessionManager
import kotlinx.coroutines.flow.first

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        
        // 1. Check if backup is enabled
        val isEnabled = prefs.driveBackupEnabledFlow.first()
        if (!isEnabled) {
            Log.d("BackupWorker", "Google Drive backup is disabled. Skipping worker.")
            return Result.success()
        }

        // 2. Check if the vault session is currently unlocked
        val masterPassword = SessionManager.masterPasswordPlain
        val activeKey = SessionManager.getKey()
        if (masterPassword == null || activeKey == null) {
            Log.d("BackupWorker", "Vault is locked or session has expired. Background backup skipped to protect security.")
            return Result.retry() // Retry when the app is next active or unlocked
        }

        // 3. Obtain mock / actual Access Token (In a full play services integration, we obtain it from GoogleSignInAccount)
        // Here we simulate checking if we have an active session. If not, we skip.
        val fakeOrRealAccessToken = "SESSION_DRIVE_OAUTH_TOKEN" // Typically retrieved from safe Datastore or Credentials Manager
        
        Log.d("BackupWorker", "Starting periodic background backup to Google Drive...")
        
        val database = AppDatabase.getDatabase(applicationContext)
        val items = database.vaultDao().getAllItemsFlow().first()

        val success = GoogleDriveBackupManager.backupToDrive(
            context = applicationContext,
            accessToken = fakeOrRealAccessToken,
            items = items,
            masterPasswordPlain = masterPassword
        )

        return if (success) {
            prefs.setLastBackupTime(System.currentTimeMillis())
            Log.d("BackupWorker", "Background backup completed successfully.")
            Result.success()
        } else {
            Log.e("BackupWorker", "Background backup failed.")
            Result.retry()
        }
    }
}
