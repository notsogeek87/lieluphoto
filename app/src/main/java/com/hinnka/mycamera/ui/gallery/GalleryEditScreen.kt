package com.hinnka.mycamera.ui.gallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.hinnka.mycamera.ui.theme.PremiumGold
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.R
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.PostEditGeometry
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.RecipeParam
import com.hinnka.mycamera.model.toEffectParams
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.ml.DepthModelManager
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.raw.SpectralFilmTuning
import com.hinnka.mycamera.raw.DngEmbeddedProfile
import com.hinnka.mycamera.raw.RawAdaptiveExposureMode
import com.hinnka.mycamera.processor.DenoiseStrength
import com.hinnka.mycamera.processor.BokehStyle
import com.hinnka.mycamera.ui.camera.LutEditBottomSheet
import com.hinnka.mycamera.ui.camera.LutEditorTarget
import com.hinnka.mycamera.ui.components.*
import com.hinnka.mycamera.ui.components.RawEditPanel
import com.hinnka.mycamera.ui.components.RawBaselineColorCorrectionBottomSheet
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.gallery.EditScreenLayout
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.ZoomSpec

import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.hinnka.mycamera.lut.VideoLutEffect
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.ui.camera.autoRotate
import com.hinnka.mycamera.ui.components.RawEditPanelContentMode
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import com.hinnka.mycamera.ui.icons.AppIcons
import kotlin.math.max

private const val EDIT_TAB_LUT = 0
private const val EDIT_TAB_FRAME = 1
private const val EDIT_TAB_ADJUSTMENTS = 2
private const val EDIT_TAB_DETAIL = 3
private const val EDIT_TAB_RAW = 4
private const val EDIT_TAB_CROP = 5
private const val DEFAULT_COMPUTATIONAL_APERTURE = 1.8f

private data class EmbeddedDngProfileUiState(
    val options: List<RawEmbeddedDngProfileOption> = emptyList(),
    val defaultProfileId: String? = null,
)

private data class PreviewRenderSignature(
    val photoId: String,
    val refreshKey: Long,
    val editLutId: String?,
    val editPhotoRecipeParams: ColorRecipeParams?,
    val editLutRecipeParams: ColorRecipeParams?,
    val editLutConfig: Any?,
    val editFrameId: String?,
    val editFrameCustomProperties: Map<String, String>,
    val editSharpening: Float,
    val editNoiseReduction: Float,
    val editChromaNoiseReduction: Float,
    val editRawExposureCompensation: Float,
    val editRawAutoExposure: Boolean,
    val editRawHighlightsAdjustment: Float,
    val editRawShadowsAdjustment: Float,
    val editRawBlackPointCorrection: Float,
    val editRawWhitePointCorrection: Float,
    val editRawLensShadingCorrectionEnabled: Boolean,
    val editRawDROMode: String,
    val editRawBlackLevelMode: String,
    val editRawCustomBlackLevel: Float,
    val editRawWhiteLevelMode: String,
    val editRawCustomWhiteLevel: Float,
    val editRawCfaCorrectionMode: String,
    val editRawDcpId: String?,
    val editRawEmbeddedDngProfileId: String?,
    val editRawHncsProfileId: String?,
    val editRawHncsRenderIntent: String,
    val editRawHncsFilmCurveMode: String,
    val editRawRenderingEngine: String,
    val editRawBaselineLutId: String?,
    val editRawBaselineRecipeParams: ColorRecipeParams?,
    val editComputationalAperture: Float?,
    val editBokehStyle: BokehStyle,
    val editFocusX: Float?,
    val editFocusY: Float?,
    val editRotationDegrees: Int,
    val editStraightenDegrees: Float,
    val editMirrorHorizontal: Boolean,
    val showOrigin: Boolean,
    val editTab: Int,
)

/**
 * 照片编辑界面
 */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    FlowPreview::class
)
@Composable
fun GalleryEditScreen(
    viewModel: GalleryViewModel,
    cameraViewModel: CameraViewModel,
    onBack: () -> Unit,
    onOpenFrameEditor: (String) -> Unit,
    onFilterManagementClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val depthModelState by remember(context.applicationContext) {
        DepthModelManager.observe(context)
    }.collectAsState()
    val isDepthModelInstalled = DepthModelManager.isInstalled(context)
    var showDepthModelDownloadDialog by remember { mutableStateOf(false) }
    var pendingVirtualAperture by remember { mutableStateOf<Float?>(null) }
    val depthModelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { DepthModelManager.importModel(context, it) }
    }

    LaunchedEffect(depthModelState, pendingVirtualAperture) {
        val pendingAperture = pendingVirtualAperture
        if (depthModelState is DepthModelDownloadState.Ready && pendingAperture != null) {
            viewModel.setComputationalAperture(pendingAperture)
            pendingVirtualAperture = null
            showDepthModelDownloadDialog = false
        }
    }

    val userPreferencesRepository = remember {
        com.hinnka.mycamera.data.ContentRepository.getInstance(context).userPreferencesRepository
    }
    val userPreferences by userPreferencesRepository.userPreferences.collectAsState(
        initial = com.hinnka.mycamera.data.UserPreferences()
    )
    val currentPhotos by viewModel.currentPhotos.collectAsState()
    val currentPhoto = currentPhotos.getOrNull(viewModel.currentPhotoIndex)
    val editSourcePhoto = currentPhoto?.relatedPhoto ?: currentPhoto
    val editLutId by viewModel.editLutId.collectAsState()
    val editLutRecipeParams by viewModel.editLutRecipeParams.collectAsState()
    val editPhotoRecipeParams by viewModel.editPhotoRecipeParams.collectAsState()
    val editLutConfig = viewModel.editLutConfig
    val availableLuts = viewModel.selectableEditLuts
    val videoSourceProfile = viewModel.editVideoSourceProfile
    val lutNameOverlayState = rememberLutNameOverlayState()
    val showPaymentDialog = viewModel.showPaymentDialog
    val isPurchased by viewModel.isPurchased.collectAsState()
    val categoryOrder by viewModel.categoryOrder.collectAsState()
    val hasCopiedEditSettings by viewModel.hasCopiedEditSettings.collectAsState()

    var isSaving by remember { mutableStateOf(false) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showImageHistogram by remember { mutableStateOf(false) }
    val frameScrollState = rememberLazyListState()
    var showBaselineLutEditSheet by remember { mutableStateOf(false) }
    var baselineLutEditId by remember { mutableStateOf<String?>(null) }
    var showRawBaselineLutSelectorSheet by remember { mutableStateOf(false) }
    val syncAdjustmentsToLut = viewModel.editSyncAdjustmentsToLut

    BackHandler {
        viewModel.exitEditMode()
        onBack()
    }

    // 预览 Bitmap 状态
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropPreviewBitmap by remember(editSourcePhoto?.id) { mutableStateOf<Bitmap?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageHistogram by remember { mutableStateOf<ImageHistogram?>(null) }

    // 边框编辑状态
    val editFrameId by viewModel.editFrameId.collectAsState()
    val availableFrames = viewModel.availableFrames
    var editFrameCustomProperties by remember { mutableStateOf(emptyMap<String, String>()) }

    val editSharpening by viewModel.editSharpening.collectAsState()
    val editNoiseReduction by viewModel.editNoiseReduction.collectAsState()
    val editChromaNoiseReduction by viewModel.editChromaNoiseReduction.collectAsState()
    val editRawExposureCompensation by viewModel.editRawExposureCompensation.collectAsState()
    val editRawAutoExposure by viewModel.editRawAutoExposure.collectAsState()
    val editRawHighlightsAdjustment by viewModel.editRawHighlightsAdjustment.collectAsState()
    val editRawShadowsAdjustment by viewModel.editRawShadowsAdjustment.collectAsState()
    val editRawBlackPointCorrection by viewModel.editRawBlackPointCorrection.collectAsState()
    val editRawWhitePointCorrection by viewModel.editRawWhitePointCorrection.collectAsState()
    val editRawLensShadingCorrectionEnabled by viewModel.editRawLensShadingCorrectionEnabled.collectAsState()
    val editRawDROMode by viewModel.editRawDROMode.collectAsState()
    val editRawBlackLevelMode by viewModel.editRawBlackLevelMode.collectAsState()
    val editRawCustomBlackLevel by viewModel.editRawCustomBlackLevel.collectAsState()
    val editRawWhiteLevelMode by viewModel.editRawWhiteLevelMode.collectAsState()
    val editRawCustomWhiteLevel by viewModel.editRawCustomWhiteLevel.collectAsState()
    val editRawCfaCorrectionMode by viewModel.editRawCfaCorrectionMode.collectAsState()
    val editRawDcpId by viewModel.editRawDcpId.collectAsState()
    val editRawEmbeddedDngProfileId by
        viewModel.editRawEmbeddedDngProfileId.collectAsState()
    val editRawHncsProfileId by viewModel.editRawHncsProfileId.collectAsState()
    val editRawHncsRenderIntent by viewModel.editRawHncsRenderIntent.collectAsState()
    val editRawHncsFilmCurveMode by viewModel.editRawHncsFilmCurveMode.collectAsState()
    val editRawBaselineLutId by viewModel.editRawBaselineLutId.collectAsState()
    val editRawBaselineRecipeParams by viewModel.editRawBaselineRecipeParams.collectAsState()
    val editRawColorEngine by viewModel.editRawRenderingEngine.collectAsState()
    val editRawToneMappingParameters by viewModel.editRawToneMappingParameters.collectAsState()
    val editRawSpectralFilmStock by viewModel.editRawSpectralFilmStock.collectAsState()
    val editRawSpectralFilmPrint by viewModel.editRawSpectralFilmPrint.collectAsState()
    val editRawSpectralFilmCDensityGain by viewModel.editRawSpectralFilmCDensityGain.collectAsState()
    val editRawSpectralFilmMDensityGain by viewModel.editRawSpectralFilmMDensityGain.collectAsState()
    val editRawSpectralFilmYDensityGain by viewModel.editRawSpectralFilmYDensityGain.collectAsState()
    val availableDcps = viewModel.availableDcps
    
    val editComputationalAperture by viewModel.editComputationalAperture.collectAsState()
    val editBokehStyle by viewModel.editBokehStyle.collectAsState()
    val editFocusX by viewModel.editFocusPointX.collectAsState()
    val editFocusY by viewModel.editFocusPointY.collectAsState()

    val editCropRect by viewModel.editCropRect.collectAsState()
    val editCropAspectOption by viewModel.editCropAspectOption.collectAsState()
    val editRotationDegrees by viewModel.editRotationDegrees.collectAsState()
    val editStraightenDegrees by viewModel.editStraightenDegrees.collectAsState()
    val editMirrorHorizontal by viewModel.editMirrorHorizontal.collectAsState()

    val editAiDenoiseStrength by viewModel.editAiDenoiseStrength.collectAsState()

    val isRaw = editSourcePhoto?.let { viewModel.isRaw(it.id) } ?: false
    val refreshKey = editSourcePhoto?.id?.let { viewModel.photoRefreshKeys[it] } ?: 0L
    val dngProfileStamp = remember(editSourcePhoto?.id, refreshKey, isRaw) {
        editSourcePhoto
            ?.takeIf { isRaw }
            ?.let { GalleryManager.getDngFile(context, it.id) }
            ?.takeIf { it.exists() }
            ?.let { it.lastModified() xor it.length() }
            ?: 0L
    }
    val embeddedProfileFallbackName = stringResource(
        R.string.settings_raw_profile_tone_map_embedded
    )
    var embeddedDngProfileUiState by remember(editSourcePhoto?.id, dngProfileStamp) {
        mutableStateOf(EmbeddedDngProfileUiState())
    }

    LaunchedEffect(
        editSourcePhoto?.id,
        dngProfileStamp,
        isRaw,
        embeddedProfileFallbackName,
    ) {
        val photo = editSourcePhoto
        embeddedDngProfileUiState = if (photo != null && isRaw) {
            withContext(Dispatchers.IO) {
                val dngFile = GalleryManager.getDngFile(context, photo.id)
                val profiles = DngEmbeddedProfile.readAllFrom(dngFile)
                val visibleProfiles = profiles.filterNot { it.isPhotonHdr }
                PLog.d(
                    "GalleryEditScreen",
                    "DNG embedded profiles: " + profiles.joinToString { profile ->
                        "${profile.id}:${profile.profileName}" +
                            "(pgtm=${profile.hasProfileGainTableMap},photon=${profile.isPhotonHdr})"
                    }
                )
                EmbeddedDngProfileUiState(
                    options = visibleProfiles.map { profile ->
                        RawEmbeddedDngProfileOption(
                            id = profile.id,
                            name = profile.profileName
                                .trim()
                                .takeUnless {
                                    it.isEmpty() || it.equals("Embedded", ignoreCase = true)
                                }
                                ?: embeddedProfileFallbackName,
                            hasProfileGainTableMap = profile.hasProfileGainTableMap,
                        )
                    },
                    defaultProfileId = visibleProfiles
                        .firstOrNull { it.id == DngEmbeddedProfile.PRIMARY_PROFILE_ID }
                        ?.id,
                )
            }
        } else {
            EmbeddedDngProfileUiState()
        }
    }
    val activeEmbeddedDngProfileId = editRawEmbeddedDngProfileId
        ?.takeIf { selectedId ->
            embeddedDngProfileUiState.options.any { it.id == selectedId }
        }
        ?: embeddedDngProfileUiState.defaultProfileId

    var showOrigin by remember { mutableStateOf(false) }

    // NEW: Samsung-style layout state
    var isSettingsPanelCollapsed by rememberSaveable { mutableStateOf(false) }
    var activeToolPanel by rememberSaveable { mutableStateOf<Int?>(null) }

    // 编辑标签页状态
    var editTab by remember { mutableIntStateOf(EDIT_TAB_LUT) }
    var areEditControlsHidden by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    val editTabsScrollState = rememberScrollState()
    val editPanelScrollState = rememberScrollState()
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var editPanelHeightPx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val isBaselineLutEditSheetVisible = showBaselineLutEditSheet && baselineLutEditId != null
    val shouldShowEditPanel = !isBaselineLutEditSheetVisible &&
        !showRawBaselineLutSelectorSheet

    fun toggleEditControls() {
        areEditControlsHidden = !areEditControlsHidden
    }

    var previewRenderRequestId by remember { mutableLongStateOf(0L) }
    val shouldCalculateImageHistogram =
        (currentPhoto?.isVideo != true && showImageHistogram) ||
            editTab == EDIT_TAB_ADJUSTMENTS ||
            isBaselineLutEditSheetVisible

    fun currentPreviewSignature(fast: Boolean = false): PreviewRenderSignature? {
        val photo = editSourcePhoto ?: return null
        val rawDevelopIsBaked = isRaw
        return PreviewRenderSignature(
            photoId = photo.id,
            refreshKey = refreshKey,
            editLutId = editLutId,
            editPhotoRecipeParams = editPhotoRecipeParams,
            editLutRecipeParams = editLutRecipeParams,
            editLutConfig = editLutConfig,
            editFrameId = editFrameId,
            editFrameCustomProperties = editFrameCustomProperties.toMap(),
            editSharpening = if (rawDevelopIsBaked) 0f else editSharpening,
            editNoiseReduction = if (fast || rawDevelopIsBaked) 0f else editNoiseReduction,
            editChromaNoiseReduction = if (fast || rawDevelopIsBaked) 0f else editChromaNoiseReduction,
            editRawExposureCompensation = if (fast || rawDevelopIsBaked) 0f else editRawExposureCompensation,
            editRawAutoExposure = if (fast || rawDevelopIsBaked) false else editRawAutoExposure,
            editRawHighlightsAdjustment = if (fast || rawDevelopIsBaked) 0f else editRawHighlightsAdjustment,
            editRawShadowsAdjustment = if (fast || rawDevelopIsBaked) 0f else editRawShadowsAdjustment,
            editRawBlackPointCorrection = if (fast || rawDevelopIsBaked) 0f else editRawBlackPointCorrection,
            editRawWhitePointCorrection = if (fast || rawDevelopIsBaked) 0f else editRawWhitePointCorrection,
            editRawLensShadingCorrectionEnabled = if (fast || rawDevelopIsBaked) false else editRawLensShadingCorrectionEnabled,
            editRawDROMode = if (fast || rawDevelopIsBaked) "" else editRawDROMode,
            editRawBlackLevelMode = if (fast || rawDevelopIsBaked) "" else editRawBlackLevelMode,
            editRawCustomBlackLevel = if (fast || rawDevelopIsBaked) 0f else editRawCustomBlackLevel,
            editRawWhiteLevelMode = if (fast || rawDevelopIsBaked) "" else editRawWhiteLevelMode,
            editRawCustomWhiteLevel = if (fast || rawDevelopIsBaked) 0f else editRawCustomWhiteLevel,
            editRawCfaCorrectionMode = if (fast || rawDevelopIsBaked) "" else editRawCfaCorrectionMode,
            editRawDcpId = if (fast || rawDevelopIsBaked) null else editRawDcpId,
            editRawEmbeddedDngProfileId =
                if (fast || rawDevelopIsBaked) null else editRawEmbeddedDngProfileId,
            editRawHncsProfileId =
                if (fast || rawDevelopIsBaked) null else editRawHncsProfileId,
            editRawHncsRenderIntent =
                if (fast || rawDevelopIsBaked) "" else editRawHncsRenderIntent.assetValue,
            editRawHncsFilmCurveMode =
                if (fast || rawDevelopIsBaked) "" else editRawHncsFilmCurveMode.persistedValue,
            editRawRenderingEngine = if (fast || rawDevelopIsBaked) "" else editRawColorEngine.name,
            // RAW develop controls above are baked into original.jpg, while baseline color
            // correction is still applied by PhotoProcessor to that bitmap preview.
            editRawBaselineLutId = editRawBaselineLutId,
            editRawBaselineRecipeParams = editRawBaselineRecipeParams,
            editComputationalAperture = if (fast) 0f else editComputationalAperture,
            editBokehStyle = editBokehStyle,
            editFocusX = editFocusX,
            editFocusY = editFocusY,
            editRotationDegrees = editRotationDegrees,
            // 裁剪页通过 Compose Canvas 实时校正，不触发防抖后的 Bitmap 重渲染。
            editStraightenDegrees = if (editTab == EDIT_TAB_CROP) 0f else editStraightenDegrees,
            editMirrorHorizontal = editMirrorHorizontal,
            showOrigin = showOrigin,
            editTab = editTab
        )
    }

    suspend fun renderPreview(
        photo: MediaData,
        maxEdge: Int,
        cancelStaleResult: Boolean
    ) {
        val requestId = ++previewRenderRequestId
        val isCropPreview = editTab == EDIT_TAB_CROP
        val bitmap = withContext(Dispatchers.IO) {
            viewModel.getPreviewBitmap(
                photo,
                useGlobalEdit = true,
                showOrigin = showOrigin,
                ignoreCrop = isCropPreview,
                ignoreStraighten = isCropPreview,
                ignoreFrame = isCropPreview,
                maxEdge = maxEdge
            )
        }
        if ((!cancelStaleResult || requestId == previewRenderRequestId) && bitmap != null) {
            if (isCropPreview) {
                if (editTab == EDIT_TAB_CROP) {
                    cropPreviewBitmap = bitmap
                }
            } else {
                previewBitmap = bitmap
            }
            isLoadingPreview = false
        }
    }



    LaunchedEffect(editSourcePhoto) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        editFrameCustomProperties = photo.metadata?.customProperties
            ?: viewModel.getEditCustomProperties(photo.id)
    }

    val rawDcpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val photo = editSourcePhoto ?: return@rememberLauncherForActivityResult
        scope.launch {
            val importedDcps = viewModel.importRawDcps(uris)
            val failedCount = uris.size - importedDcps.size
            importedDcps.lastOrNull()?.let {
                viewModel.saveRawDcpSelection(photo, it.id) {
                    viewModel.refreshRawPreview(
                        photo = photo,
                    )
                }
            }
            when {
                importedDcps.size == 1 && failedCount == 0 -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_success, importedDcps.first().getName()), Toast.LENGTH_SHORT).show()
                }
                importedDcps.isNotEmpty() && failedCount == 0 -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_success_count, importedDcps.size), Toast.LENGTH_SHORT).show()
                }
                importedDcps.isNotEmpty() -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_partial, importedDcps.size, failedCount), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(context, context.getString(R.string.raw_dcp_import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey, isRaw) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        // RAW decoding is already the expensive source load. Running the fast JPEG-style pass
        // before the full pass decodes the same DNG twice for every refresh.
        if (photo.isVideo || isRaw) return@LaunchedEffect
        snapshotFlow {
            currentPreviewSignature(true)
        }
            .filter { it != null }
            .conflate()
            .collect {
                renderPreview(photo, maxEdge = 1440, cancelStaleResult = false)
            }
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey) {
        cropPreviewBitmap = null
    }

    LaunchedEffect(editSourcePhoto?.id, refreshKey) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        if (photo.isVideo) return@LaunchedEffect
        snapshotFlow {
            currentPreviewSignature()
        }
            .filter { it != null }
            .conflate()
            .debounce(250)
            .collectLatest {
                renderPreview(photo, maxEdge = 4096, cancelStaleResult = true)
            }
    }

    LaunchedEffect(editSourcePhoto) {
        val photo = editSourcePhoto ?: return@LaunchedEffect
        thumbnailBitmap = withContext(Dispatchers.IO) {
            viewModel.loadThumbnail(photo)
        }
    }

    LaunchedEffect(shouldCalculateImageHistogram, previewBitmap) {
        val bitmap = previewBitmap
        if (!shouldCalculateImageHistogram || bitmap == null || bitmap.isRecycled) {
            imageHistogram = null
            return@LaunchedEffect
        }

        imageHistogram = try {
            withContext(Dispatchers.Default) {
                ImageHistogram.fromBitmap(bitmap)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PLog.e(
                "GalleryEditScreen",
                "Failed to calculate histogram from the current preview",
                error,
            )
            null
        }
    }

    LaunchedEffect(editFrameId) {
        editFrameId?.let { lutId ->
            val selectedIndex = availableFrames.indexOfFirst { it.id == lutId }
            if (selectedIndex >= 1) {
                frameScrollState.animateScrollToItem(selectedIndex - 1)
            }
        }
    }

    if (currentPhoto == null) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }
    val currentEditSourcePhoto = editSourcePhoto ?: currentPhoto
    val currentEditMetadata = currentEditSourcePhoto.metadata ?: viewModel.currentMediaMetadata
    val isImportedRaw = currentEditMetadata?.isImported == true && isRaw

    val previewSourceWidth = previewBitmap?.width?.takeIf { it > 0 }
        ?: currentPhoto.width.takeIf { it > 0 }
    val previewSourceHeight = previewBitmap?.height?.takeIf { it > 0 }
        ?: currentPhoto.height.takeIf { it > 0 }
    val visibleEditPanelHeightPx = if (shouldShowEditPanel && !areEditControlsHidden) {
        editPanelHeightPx
    } else {
        0
    }
    val visibleTopBarHeightPx = if (areEditControlsHidden) 0 else topBarHeightPx
    val targetPreviewOffsetYPx = if (
        viewportSize != IntSize.Zero &&
        previewSourceWidth != null &&
        previewSourceHeight != null
    ) {
        val viewportWidth = viewportSize.width.toFloat()
        val viewportHeight = viewportSize.height.toFloat()
        val sourceAspectRatio = previewSourceWidth.toFloat() / previewSourceHeight.toFloat()
        val viewportAspectRatio = viewportWidth / viewportHeight
        val displayedPhotoHeight = if (sourceAspectRatio > viewportAspectRatio) {
            viewportWidth / sourceAspectRatio
        } else {
            viewportHeight
        }
        val remainingTop = visibleTopBarHeightPx.toFloat()
            .coerceIn(0f, viewportHeight)
        val remainingBottom = (viewportHeight - visibleEditPanelHeightPx.toFloat())
            .coerceIn(remainingTop, viewportHeight)
        val remainingHeight = remainingBottom - remainingTop
        val targetPhotoTop = if (displayedPhotoHeight <= remainingHeight) {
            remainingTop + (remainingHeight - displayedPhotoHeight) / 2f
        } else {
            (remainingBottom - displayedPhotoHeight).coerceAtLeast(0f)
        }
        val fullScreenCenteredPhotoTop = (viewportHeight - displayedPhotoHeight) / 2f
        targetPhotoTop - fullScreenCenteredPhotoTop
    } else {
        0f
    }
    val previewOffsetYPx by animateFloatAsState(
        targetValue = targetPreviewOffsetYPx,
        label = "editPreviewVerticalPosition"
    )

    var pendingRawPreviewRefresh by remember(currentEditSourcePhoto.id) { mutableStateOf(false) }
    var pendingPhotonPgtmRegeneration by remember(currentEditSourcePhoto.id) { mutableStateOf(false) }
    var pendingRawRefreshToast by remember(currentEditSourcePhoto.id) { mutableStateOf(false) }
    val isRefreshingRawPreview = viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id)

    fun refreshRawPreview(
        showResultToast: Boolean = false,
        forceRegeneratePhotonPgtm: Boolean = false,
    ) {
        viewModel.refreshRawPreview(
            photo = currentEditSourcePhoto,
            forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
        ) { success ->
            if (showResultToast) {
                Toast.makeText(
                    context,
                    if (success) R.string.refresh_success else R.string.refresh_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun requestRawPreviewRefresh(
        showResultToast: Boolean = false,
        forceRegeneratePhotonPgtm: Boolean = false,
    ) {
        if (!isRaw) return
        if (viewModel.refreshingPhotos.contains(currentEditSourcePhoto.id)) {
            pendingPhotonPgtmRegeneration = pendingPhotonPgtmRegeneration || forceRegeneratePhotonPgtm
            pendingRawRefreshToast = pendingRawRefreshToast || showResultToast
            pendingRawPreviewRefresh = true
            return
        }
        refreshRawPreview(
            showResultToast = showResultToast,
            forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
        )
    }

    LaunchedEffect(isRefreshingRawPreview, pendingRawPreviewRefresh, currentEditSourcePhoto.id) {
        if (!isRefreshingRawPreview && pendingRawPreviewRefresh) {
            val forceRegeneratePhotonPgtm = pendingPhotonPgtmRegeneration
            val showResultToast = pendingRawRefreshToast
            pendingRawPreviewRefresh = false
            pendingPhotonPgtmRegeneration = false
            pendingRawRefreshToast = false
            refreshRawPreview(
                showResultToast = showResultToast,
                forceRegeneratePhotonPgtm = forceRegeneratePhotonPgtm,
            )
        }
    }

    // NEW: Samsung-style layout - Image is ALWAYS visible, NEVER overlain
    SamsungEditLayout(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.exitEditMode()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = Color.White
                        )
                    }
                }
            )
        },
        imageArea = {
            Box(modifier = Modifier.fillMaxSize()) {
                if (currentPhoto?.isVideo == true && videoSourceProfile != null) {
                    VideoEditPlayer(
                        photo = currentPhoto,
                        lutConfig = editLutConfig,
                        recipeParams = editLutRecipeParams ?: editPhotoRecipeParams,
                        sourceLogProfile = videoSourceProfile,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ZoomableEditImage(
                        previewBitmap = previewBitmap,
                        isLutEditing = editTab == EDIT_TAB_LUT,
                        contentDescription = stringResource(R.string.gallery_edit_preview),
                        onZoomChange = { isZoomed = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (showOrigin) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.gallery_original),
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        },
        toolPanel = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tool icons - one for each tab, showing which tool is active
                EditToolIcon(
                    icon = AppIcons.Palette,
                    label = stringResource(R.string.gallery_lut),
                    isSelected = editTab == EDIT_TAB_LUT,
                    onClick = { 
                        editTab = EDIT_TAB_LUT
                        activeToolPanel = EDIT_TAB_LUT
                    }
                )
                EditToolIcon(
                    icon = AppIcons.FilterFrames,
                    label = stringResource(R.string.gallery_frame),
                    isSelected = editTab == EDIT_TAB_FRAME,
                    onClick = { 
                        editTab = EDIT_TAB_FRAME
                        activeToolPanel = EDIT_TAB_FRAME
                    }
                )
                EditToolIcon(
                    icon = AppIcons.Tune,
                    label = stringResource(R.string.gallery_adjustments),
                    isSelected = editTab == EDIT_TAB_ADJUSTMENTS,
                    onClick = { 
                        editTab = EDIT_TAB_ADJUSTMENTS
                        activeToolPanel = EDIT_TAB_ADJUSTMENTS
                    }
                )
                EditToolIcon(
                    icon = AppIcons.Contrast,
                    label = stringResource(R.string.gallery_detail),
                    isSelected = editTab == EDIT_TAB_DETAIL,
                    onClick = { 
                        editTab = EDIT_TAB_DETAIL
                        activeToolPanel = EDIT_TAB_DETAIL
                    }
                )
                EditToolIcon(
                    icon = AppIcons.Output,
                    label = stringResource(R.string.gallery_raw),
                    isSelected = editTab == EDIT_TAB_RAW,
                    onClick = { 
                        editTab = EDIT_TAB_RAW
                        activeToolPanel = EDIT_TAB_RAW
                    }
                )
                EditToolIcon(
                    icon = AppIcons.Crop169,
                    label = stringResource(R.string.gallery_crop),
                    isSelected = editTab == EDIT_TAB_CROP,
                    onClick = { 
                        editTab = EDIT_TAB_CROP
                        activeToolPanel = EDIT_TAB_CROP
                    }
                )
            }
        },
        settingsPanel = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (editTab) {
                    EDIT_TAB_LUT -> {
                        // LUT Settings Panel - Horizontal scrollable preview rail
                        SettingsPanelHeader(
                            title = stringResource(R.string.gallery_lut),
                            isCollapsed = isSettingsPanelCollapsed,
                            onToggleCollapse = { isSettingsPanelCollapsed = !isSettingsPanelCollapsed }
                        )
                        if (availableLuts.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                state = frameScrollState
                            ) {
                                items(availableLuts) { lut ->
                                    FrameOption(
                                        name = lut.title,
                                        previewBitmap = thumbnailBitmap,
                                        isSelected = lut.id == editLutId,
                                        isVip = lut.isVip,
                                        isCustom = lut.isCustom,
                                        isEditable = lut.isEditable,
                                        onClick = { viewModel.setEditLutId(lut.id) }
                                    )
                                }
                            }
                        }
                    }
                    EDIT_TAB_FRAME -> {
                        // Frame Settings Panel - Horizontal scrollable preview rail
                        SettingsPanelHeader(
                            title = stringResource(R.string.gallery_frame),
                            isCollapsed = isSettingsPanelCollapsed,
                            onToggleCollapse = { isSettingsPanelCollapsed = !isSettingsPanelCollapsed }
                        )
                        if (availableFrames.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                state = frameScrollState
                            ) {
                                items(availableFrames) { frame ->
                                    FrameOption(
                                        name = frame.name,
                                        previewBitmap = null,
                                        isSelected = frame.id == editFrameId,
                                        isVip = frame.isVip,
                                        isCustom = frame.isCustom,
                                        onClick = { viewModel.setEditFrameId(frame.id) }
                                    )
                                }
                            }
                        }
                    }
                    EDIT_TAB_ADJUSTMENTS -> {
                        // Adjustments Settings Panel - Sliders
                        SettingsPanelHeader(
                            title = stringResource(R.string.gallery_adjustments),
                            isCollapsed = isSettingsPanelCollapsed,
                            onToggleCollapse = { isSettingsPanelCollapsed = !isSettingsPanelCollapsed }
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.gallery_sharpening),
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 14.sp
                            )
                            Slider(
                                value = editSharpening,
                                onValueChange = { viewModel.setEditSharpening(it) },
                                valueRange = 0f..2f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.gallery_noise_reduction),
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 14.sp
                            )
                            Slider(
                                value = editNoiseReduction,
                                onValueChange = { viewModel.setEditNoiseReduction(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    EDIT_TAB_DETAIL -> {
                        // Detail Settings Panel - Bokeh style selector + aperture slider
                        SettingsPanelHeader(
                            title = stringResource(R.string.gallery_detail),
                            isCollapsed = isSettingsPanelCollapsed,
                            onToggleCollapse = { isSettingsPanelCollapsed = !isSettingsPanelCollapsed }
                        )
                        Column {
                            BokehStyleSelector(
                                selectedStyle = editBokehStyle,
                                onStyleSelected = { viewModel.setEditBokehStyle(it) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.gallery_computational_aperture),
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 14.sp
                            )
                            Slider(
                                value = editComputationalAperture ?: DEFAULT_COMPUTATIONAL_APERTURE,
                                onValueChange = { viewModel.setComputationalAperture(it) },
                                valueRange = 0.1f..32f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    EDIT_TAB_RAW -> {
                        // RAW Settings Panel
                        SettingsPanelHeader(
                            title = stringResource(R.string.gallery_raw),
                            isCollapsed = isSettingsPanelCollapsed,
                            onToggleCollapse = { isSettingsPanelCollapsed = !isSettingsPanelCollapsed }
                        )
                        RawEditPanel(
                            contentMode = RawEditPanelContentMode.FULL,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    EDIT_TAB_CROP -> {
                        // Crop Settings Panel
                        SettingsPanelHeader(
                            title = stringResource(R.string.gallery_crop),
                            isCollapsed = isSettingsPanelCollapsed,
                            onToggleCollapse = { isSettingsPanelCollapsed = !isSettingsPanelCollapsed }
                        )
                        CropEditPanel(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        tabBar = {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SamsungEditTabItem(
                    title = stringResource(R.string.gallery_lut),
                    isSelected = editTab == EDIT_TAB_LUT,
                    onClick = { 
                        editTab = EDIT_TAB_LUT
                        activeToolPanel = EDIT_TAB_LUT
                    }
                )
                SamsungEditTabItem(
                    title = stringResource(R.string.gallery_frame),
                    isSelected = editTab == EDIT_TAB_FRAME,
                    onClick = { 
                        editTab = EDIT_TAB_FRAME
                        activeToolPanel = EDIT_TAB_FRAME
                    }
                )
                SamsungEditTabItem(
                    title = stringResource(R.string.gallery_adjustments),
                    isSelected = editTab == EDIT_TAB_ADJUSTMENTS,
                    onClick = { 
                        editTab = EDIT_TAB_ADJUSTMENTS
                        activeToolPanel = EDIT_TAB_ADJUSTMENTS
                    }
                )
                SamsungEditTabItem(
                    title = stringResource(R.string.gallery_detail),
                    isSelected = editTab == EDIT_TAB_DETAIL,
                    onClick = { 
                        editTab = EDIT_TAB_DETAIL
                        activeToolPanel = EDIT_TAB_DETAIL
                    }
                )
                SamsungEditTabItem(
                    title = stringResource(R.string.gallery_raw),
                    isSelected = editTab == EDIT_TAB_RAW,
                    onClick = { 
                        editTab = EDIT_TAB_RAW
                        activeToolPanel = EDIT_TAB_RAW
                    }
                )
                SamsungEditTabItem(
                    title = stringResource(R.string.gallery_crop),
                    isSelected = editTab == EDIT_TAB_CROP,
                    onClick = { 
                        editTab = EDIT_TAB_CROP
                        activeToolPanel = EDIT_TAB_CROP
                    }
                )
            }
        },
        isSettingsPanelVisible = shouldShowEditPanel && activeToolPanel != null,
        isSettingsPanelCollapsed = isSettingsPanelCollapsed
    )

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
internal fun BokehStyleSelector(
    selectedStyle: BokehStyle,
    onStyleSelected: (BokehStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.gallery_bokeh_style_title),
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BokehStyle.entries.forEach { style ->
                BokehStyleButton(
                    style = style,
                    selected = selectedStyle == style,
                    onClick = { onStyleSelected(style) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun BokehStyleButton(
    style: BokehStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val previewResource = when (style) {
        BokehStyle.DEFAULT -> R.drawable.bokeh_style_swirl_preview
        BokehStyle.NATURAL -> R.drawable.bokeh_style_natural_preview
        BokehStyle.BUBBLE -> R.drawable.bokeh_style_bubble_preview
    }
    val labelResource = when (style) {
        BokehStyle.DEFAULT -> R.string.gallery_bokeh_style_default
        BokehStyle.NATURAL -> R.string.gallery_bokeh_style_natural
        BokehStyle.BUBBLE -> R.string.gallery_bokeh_style_bubble
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF121212))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) AccentColor else Color.White.copy(alpha = 0.22f),
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(previewResource),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(7.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = stringResource(labelResource),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
internal fun CompactToggleChip(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 12.dp else 2.dp,
        label = "compactToggleThumb"
    )
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (checked) AccentColor.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.1f)
            )
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = if (checked) AccentColor else Color.White.copy(alpha = 0.72f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 14.dp)
                .clip(CircleShape)
                .background(
                    if (checked) AccentColor
                    else Color.White.copy(alpha = 0.18f)
                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset, y = 2.dp)
                    .size(10.dp)
                    .background(
                        if (checked) OnAccentColor else Color.White.copy(alpha = 0.78f),
                        CircleShape
                    )
            )
        }
    }
}

/**
 * 标签页项
 */
@Composable
internal fun TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 2.dp)
                .background(if (isSelected) AccentColor else Color.Transparent)
        )
    }
}

/**
 * LUT 选项
 */
@Composable
internal fun FrameOption(
    name: String,
    previewBitmap: Bitmap? = null,
    isSelected: Boolean,
    isVip: Boolean = false,
    isCustom: Boolean = false,  // 添加自定义标识参数
    isEditable: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) AccentColor.copy(alpha = 0.3f)
                    else Color.White.copy(alpha = 0.1f)
                )
                .then(
                    if (isSelected) Modifier.border(2.dp, AccentColor, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.take(2).uppercase(),
                    color = if (isSelected) AccentColor else Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            }

            if (isSelected && isEditable) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Tune,
                            contentDescription = stringResource(R.string.edit),
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (isVip) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = PremiumGold,
                            shape = RoundedCornerShape(bottomStart = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.billing_vip_tag),
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 8.sp
                    )
                }
            }

            // 自定义标识
            if (isCustom) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            color = Color(0xFF4CAF50),  // 绿色表示自定义
                            shape = RoundedCornerShape(bottomEnd = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.custom_tag),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = name,
            color = if (isSelected) AccentColor else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}


/**
 * 用于编辑界面的可缩放图片组件
 * 使用 Telephoto 库支持大尺寸图片查看和缩放
 */
@Composable
internal fun ZoomableEditImage(
    previewBitmap: Bitmap?,
    isLutEditing: Boolean,
    contentDescription: String,
    onZoomChange: (isZoomed: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxZoom = previewBitmap?.let { max(it.width, it.height) / 500f } ?: 1f
    val zoomableState = rememberZoomableImageState(
        zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = maxZoom))
    )

    LaunchedEffect(zoomableState.zoomableState.zoomFraction) {
        onZoomChange((zoomableState.zoomableState.zoomFraction ?: 0f) > 0.01f)
    }

    LaunchedEffect(isLutEditing) {
        if (isLutEditing) {
            zoomableState.zoomableState.resetZoom()
        }
    }

    val model = ImageRequest.Builder(context)
        .data(previewBitmap)
        .crossfade(true)
        .build()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ZoomableAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            state = zoomableState,
            onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 3f),
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
internal fun <T> SegmentedControl(
    title: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: @Composable (T) -> String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(items) { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onItemSelected(item) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = itemLabel(item),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun VideoEditPlayer(
    photo: MediaData,
    lutConfig: LutConfig?,
    recipeParams: ColorRecipeParams?,
    sourceLogProfile: VideoLogProfile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaUri = remember(photo.id, photo.uri, photo.sourceUri) {
        photo.sourceUri ?: photo.uri
    }
    
    PLog.d("VideoEditPlayer", "VideoEditPlayer composable recomposing/initializing. photoId: ${photo.id}, mediaUri: $mediaUri")

    var isPlayerActive by remember { mutableStateOf(false) }
    LaunchedEffect(photo.id) {
        delay(150) // Wait for the transitions to complete and EGL context release
        isPlayerActive = true
    }

    val videoLutEffect = remember(photo.id, mediaUri, sourceLogProfile) {
        PLog.d("VideoEditPlayer", "Instantiating new VideoLutEffect.")
        VideoLutEffect(lutConfig, recipeParams, sourceLogProfile)
    }
    
    val exoPlayer = remember(photo.id, mediaUri, isPlayerActive, videoLutEffect) {
        if (!isPlayerActive) return@remember null
        PLog.d("VideoEditPlayer", "Re-creating loopable ExoPlayer instance for video preview.")
        ExoPlayer.Builder(context, VideoEditRenderersFactory(context, sourceLogProfile.isEnabled)).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            repeatMode = Player.REPEAT_MODE_ONE
            setVideoEffects(listOf(videoLutEffect))
            prepare()
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    PLog.d("VideoEditPlayer", "ExoPlayer state changed: $state")
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    PLog.e("VideoEditPlayer", "ExoPlayer encountered playback error!", error)
                }
            })
        }
    }

    LaunchedEffect(lutConfig, recipeParams, exoPlayer) {
        PLog.d("VideoEditPlayer", "Updating VideoLutEffect with lutConfig: ${lutConfig?.title}, recipeParams: ${recipeParams != null}")
        videoLutEffect.update(lutConfig, recipeParams)

        // A paused player does not submit another frame to the GL effect pipeline. REDRAW asks the
        // Media3 video graph to process its retained frame again without seeking or changing the
        // playback state, so the controller stays hidden and the new LUT is visible immediately.
        if (exoPlayer != null && !exoPlayer.playWhenReady) {
            exoPlayer.setVideoEffects(VideoFrameProcessor.REDRAW)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            if (exoPlayer != null) {
                PLog.d("VideoEditPlayer", "Disposing loopable ExoPlayer.")
                exoPlayer.release()
            }
        }
    }

    if (exoPlayer != null) {
        AndroidView(
            factory = {
                PLog.d("VideoEditPlayer", "Creating PlayerView factory.")
                LayoutInflater.from(context).inflate(R.layout.view_motion_photo_player, null) as PlayerView
            },
            update = {
                PLog.d("VideoEditPlayer", "Updating PlayerView with ExoPlayer.")
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                it.useController = true
                it.controllerAutoShow = true
                it.player = exoPlayer
                it.visibility = android.view.View.VISIBLE
            },
            modifier = modifier.autoRotate(matchParentSize = true)
        )
    } else {
        Spacer(modifier = modifier.fillMaxSize())
    }
}
