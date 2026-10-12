package com.myclinic.app.data.files

/**
 * Where a photo or PDF added while offline waits, encrypted, until the sync
 * uploads it. Each phone provides its own (Android Keystore / iPhone data
 * protection). Implementations never use the storage path (which contains
 * the patient id) as a file name.
 */
interface StagedFileVault {
    suspend fun put(path: String, bytes: ByteArray)
    suspend fun get(path: String): ByteArray?
    fun contains(path: String): Boolean
    suspend fun remove(path: String)
    /** Deletes everything waiting (on sign-out, or when the key is lost). */
    fun clear()
}
