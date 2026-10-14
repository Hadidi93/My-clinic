package com.myclinic.app.ui.files

import com.myclinic.app.data.files.FileTooLargeException
import com.myclinic.app.data.files.PickedFile
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The screen on top, to show a picker over it. */
internal fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/**
 * Resizes a photo so its longest side is at most [maxDimension] and saves it
 * as JPEG. Drawing it afresh drops all metadata (GPS position, camera details).
 */
internal fun UIImage.toJpeg(maxDimension: Int, quality: Double): ByteArray {
    val (w, h) = size.useContents { width to height }
    val scale = minOf(1.0, maxDimension / maxOf(w, h, 1.0))
    val target = CGSizeMake(w * scale, h * scale)
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val resized = UIGraphicsImageRenderer(size = target, format = format).imageWithActions { _ ->
        drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale))
    }
    val data = UIImageJPEGRepresentation(resized, quality) ?: error("Could not save the photo")
    return data.toByteArray()
}

/** Photo library (no permission needed: the system picker only hands over what the user chose). */
internal class PhotoLibraryPicker(private val onImage: (read: suspend () -> UIImage) -> Unit) : NSObject(),
    PHPickerViewControllerDelegateProtocol {

    fun show() {
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = this
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        onImage {
            suspendCancellableCoroutine { cont ->
                result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, error ->
                    val image = data?.let { UIImage(data = it) }
                    if (image != null) cont.resume(image) else cont.resumeWithException(IllegalStateException(error?.localizedDescription ?: "No image"))
                }
            }
        }
    }
}

/** The camera (asks for camera permission the first time). */
internal class CameraPicker(private val onImage: (read: suspend () -> UIImage) -> Unit) : NSObject(),
    UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    val available: Boolean
        get() = UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    fun show() {
        if (!available) return
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            delegate = this@CameraPicker
        }
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        // The photo is only in memory: it is never saved to the phone's photo library.
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        onImage { image }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

/** A PDF from the Files app (iCloud Drive, downloads, other apps). */
internal class PdfPicker(private val onFile: (read: suspend () -> PickedFile) -> Unit) : NSObject(),
    UIDocumentPickerDelegateProtocol {

    fun show() {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
        picker.delegate = this
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        onFile {
            try {
                val data = NSData.dataWithContentsOfURL(url) ?: error("Cannot read file")
                if (data.length.toLong() > PickedFile.MAX_BYTES) throw FileTooLargeException()
                PickedFile(data.toByteArray(), PickedFile.MIME_PDF, url.lastPathComponent?.take(200))
            } finally {
                // "asCopy" put a copy in our temporary folder: never leave it behind.
                url.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
            }
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}
