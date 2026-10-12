package com.myclinic.app.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** Opens the iPhone offline copy. */
object IosLocalDatabase {
    /**
     * The database lives in the app's private Application Support folder,
     * which is marked "complete protection": iOS keeps the files encrypted
     * (AES-256, hardware key tied to the phone's passcode) whenever the phone
     * is locked. It is a cache: if it can't be read, it is downloaded again.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun open(): LocalDatabase {
        val manager = NSFileManager.defaultManager
        val support: NSURL = requireNotNull(
            manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null),
        )
        val dir = requireNotNull(support.URLByAppendingPathComponent("MyClinic", isDirectory = true))
        val dirPath = requireNotNull(dir.path)
        manager.createDirectoryAtPath(dirPath, withIntermediateDirectories = true, attributes = null, error = null)
        // New files in this folder (the database and its journal) get the same protection.
        manager.setAttributes(mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete), ofItemAtPath = dirPath, error = null)
        return Room.databaseBuilder<LocalDatabase>(name = "$dirPath/${LocalDatabase.FILE_NAME}")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}
