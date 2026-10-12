package com.myclinic.app.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Room
import com.myclinic.app.data.security.KeystoreCipher
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/** Opens the Android offline copy. */
object AndroidLocalDatabase {
    /**
     * Opens the offline cache, encrypted with SQLCipher (AES-256). The
     * database password is random, generated on first use, and stored only
     * in encrypted form (key held in Android Keystore). If that password is
     * ever lost, the cache is deleted and simply downloaded again.
     */
    fun open(context: Context): LocalDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKey.getOrCreate(context)
        return Room.databaseBuilder(context, LocalDatabase::class.java, LocalDatabase.FILE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .fallbackToDestructiveMigration(dropAllTables = true) // it's a cache: the server is the source of truth
            .build()
    }
}

/** Generates and stores the random database password, encrypted with a Keystore key. */
private object DatabaseKey {
    private const val PREFS = "secure_db"
    private const val KEY = "db_passphrase"

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cipher = KeystoreCipher("myclinic_db_key")
        prefs.getString(KEY, null)?.let { stored ->
            runCatching { return Base64.decode(cipher.decrypt(stored), Base64.NO_WRAP) }
            // Can't decrypt (e.g. Keystore reset): start a fresh, empty cache.
            context.deleteDatabase(LocalDatabase.FILE_NAME)
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY, cipher.encrypt(Base64.encodeToString(bytes, Base64.NO_WRAP))).commit()
        return bytes
    }
}
