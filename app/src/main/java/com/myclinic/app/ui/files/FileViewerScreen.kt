package com.myclinic.app.ui.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.files.ClinicalFileStore
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.FullScreenError
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.navigation.FileViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class FileViewerUiState(
    val loading: Boolean = true,
    /** One image for a photo, one per page for a PDF. */
    val pages: List<ImageBitmap> = emptyList(),
    val truncated: Boolean = false,
    val error: DataError? = null,
)

/**
 * Opens one clinical file. The file is downloaded into memory and drawn on
 * screen; it is never saved to the phone's storage or shared with other apps.
 */
@HiltViewModel
class FileViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val files: ClinicalFileStore,
) : ViewModel() {

    val route: FileViewerRoute = savedStateHandle.toRoute()
    private val _state = MutableStateFlow(FileViewerUiState())
    val state: StateFlow<FileViewerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FileViewerUiState()
        viewModelScope.launch {
            try {
                val bytes = files.load(route.path)
                val pages = withContext(Dispatchers.Default) {
                    if (route.mimeType == PickedFile.MIME_PDF) renderPdf(bytes) else listOf(decodeImage(bytes)) to false
                }
                _state.value = FileViewerUiState(loading = false, pages = pages.first, truncated = pages.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = FileViewerUiState(loading = false, error = e.toDataError())
            }
        }
    }

    private fun decodeImage(bytes: ByteArray): ImageBitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > MAX_IMAGE_PX) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Not an image")
        return bitmap.asImageBitmap()
    }

    /** PdfRenderer needs a file: it is written to private storage and deleted straight after opening. */
    private suspend fun renderPdf(bytes: ByteArray): Pair<List<ImageBitmap>, Boolean> = withContext(Dispatchers.IO) {
        val dir = File(context.noBackupFilesDir, "viewer").apply { mkdirs() }
        val tmp = File(dir, "${UUID.randomUUID()}.pdf")
        tmp.writeBytes(bytes)
        val fd = try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            tmp.delete() // the open descriptor still reads it; nothing stays on disk
        }
        fd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = minOf(renderer.pageCount, MAX_PDF_PAGES)
                val pages = (0 until count).map { i ->
                    renderer.openPage(i).use { page ->
                        val scale = PDF_WIDTH_PX.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(PDF_WIDTH_PX, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    }
                }
                pages to (renderer.pageCount > MAX_PDF_PAGES)
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_PX = 3000
        const val PDF_WIDTH_PX = 1400
        const val MAX_PDF_PAGES = 30
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(onBack: () -> Unit, viewModel: FileViewerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(viewModel.route.title ?: stringResource(R.string.file_viewer_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.loading -> FullScreenLoading()
                    state.error != null -> FullScreenError(state.error!!.message(), onRetry = viewModel::load)
                    state.pages.size == 1 && viewModel.route.mimeType != PickedFile.MIME_PDF -> ZoomableImage(state.pages.first())
                    else -> LazyColumn(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.pages) { i, page ->
                            Image(page, contentDescription = stringResource(R.string.file_page, i + 1),
                                contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                        }
                        if (state.truncated) {
                            item { Text(stringResource(R.string.file_pdf_truncated), modifier = Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** Pinch to zoom, drag to move. */
@Composable
private fun ZoomableImage(image: ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).transformable(transform),
        contentAlignment = Alignment.Center) {
        Image(
            image,
            contentDescription = stringResource(R.string.file_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y,
            ),
        )
    }
}
