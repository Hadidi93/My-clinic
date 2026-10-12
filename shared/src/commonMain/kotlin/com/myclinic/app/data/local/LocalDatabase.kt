package com.myclinic.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * The offline copy of the doctor's patients (and the outbox of changes not
 * uploaded yet). Each phone opens it its own way, encrypted: SQLCipher with
 * a Keystore-protected password on Android, iPhone data protection on iOS.
 */
@Database(
    entities = [CachedRowEntity::class, PendingOpEntity::class, SyncMetaEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(LocalDatabaseConstructor::class)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao

    companion object {
        const val FILE_NAME = "myclinic-cache.db"
    }
}

/** Filled in by Room's code generator for each platform. */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalDatabaseConstructor : RoomDatabaseConstructor<LocalDatabase> {
    override fun initialize(): LocalDatabase
}
