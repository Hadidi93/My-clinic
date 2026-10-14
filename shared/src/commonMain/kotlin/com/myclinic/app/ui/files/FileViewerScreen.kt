package com.myclinic.app.ui.files

import com.myclinic.shared.resources.*
import org.koin.compose.viewmodel.koinViewModel
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.DataError
import com.myclinic.app.data.consults.ConsultRepository
import com.myclinic.app.data.files.ClinicalFileStore
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.FullScreenError
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.navigation.FileViewerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
class FileViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val renderer: FileRenderer,
    private val files: ClinicalFileStore,
    private val consults: ConsultRepository,
) : ViewModel() {

    val route = FileViewerRoute(
        path = checkNotNull(savedStateHandle.get<String>(FileViewerRoute::path.name)),
        mimeType = checkNotNull(savedStateHandle.get<String>(FileViewerRoute::mimeType.name)),
        title = savedStateHandle.get<String>(FileViewerRoute::title.name),
        consultFile = savedStateHandle.get<Boolean>(FileViewerRoute::consultFile.name) ?: false,
    )
    private val _state = MutableStateFlow(FileViewerUiState())
    val state: StateFlow<FileViewerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FileViewerUiState()
        viewModelScope.launch {
            try {
                val bytes = if (route.consultFile) consults.loadConsultFile(route.path) else files.load(route.path)
                val pages = if (route.mimeType == PickedFile.MIME_PDF) {
                    renderer.pdfPages(bytes, widthPx = PDF_WIDTH_PX, maxPages = MAX_PDF_PAGES)
                } else {
                    listOf(renderer.image(bytes, maxPx = MAX_IMAGE_PX)) to false
                }
                _state.value = FileViewerUiState(loading = false, pages = pages.first, truncated = pages.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = FileViewerUiState(loading = false, error = e.toDataError())
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
fun FileViewerScreen(onBack: () -> Unit, viewModel: FileViewerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(viewModel.route.title ?: stringResource(Res.string.file_viewer_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
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
                            Image(page, contentDescription = stringResource(Res.string.file_page, i + 1),
                                contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                        }
                        if (state.truncated) {
                            item { Text(stringResource(Res.string.file_pdf_truncated), modifier = Modifier.padding(16.dp)) }
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
            contentDescription = stringResource(Res.string.file_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y,
            ),
        )
    }
}
