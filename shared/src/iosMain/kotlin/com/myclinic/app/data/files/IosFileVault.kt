package com.myclinic.app.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import com.myclinic.app.ui.files.toByteArray
import com.myclinic.app.ui.files.toNSData

/**
 * iPhone's [StagedFileVault]: files in the app's private Application Support
 * folder with complete data protection (iOS keeps them encrypted while the
 * phone is locked). File names are a SHA-256 of the storage path.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileVault : StagedFileVault {
    private val manager = NSFileManager.defaultManager

    private val dir: String by lazy {
        val support = manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val path = requireNotNull(support?.path) + "/MyClinic/pending"
        manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        manager.setAttributes(mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete), ofItemAtPath = path, error = null)
        path
    }

    private fun file(path: String) = "$dir/${sha256(path)}"

    override suspend fun put(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        bytes.toNSData().writeToFile(file(path), atomically = true)
        manager.setAttributes(mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete), ofItemAtPath = file(path), error = null)
        Unit
    }

    override suspend fun get(path: String): ByteArray? = withContext(Dispatchers.IO) {
        NSData.dataWithContentsOfFile(file(path))?.toByteArray()
    }

    override fun contains(path: String): Boolean = manager.fileExistsAtPath(file(path))

    override suspend fun remove(path: String) {
        withContext(Dispatchers.IO) { manager.removeItemAtPath(file(path), error = null) }
    }

    override fun clear() {
        manager.contentsOfDirectoryAtPath(dir, error = null)?.forEach { name ->
            manager.removeItemAtPath("$dir/$name", error = null)
        }
    }

    private fun sha256(text: String): String = memScoped {
        val input = text.encodeToByteArray()
        val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { CC_SHA256(it.addressOf(0), input.size.toUInt(), digest) }
        (0 until CC_SHA256_DIGEST_LENGTH).joinToString("") { digest[it].toString(16).padStart(2, '0') }
    }
}
