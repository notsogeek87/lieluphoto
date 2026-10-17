package com.hinnka.mycamera.ui.camera

import android.media.AudioDeviceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.OnAccentColor
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.AspectRatio
import com.hinnka.mycamera.camera.MeteringMode
import com.hinnka.mycamera.raw.DcpInfo
import com.hinnka.mycamera.raw.HncsFilmCurveMode
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.raw.RawToneMappingParameters
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.ui.components.AppModalBottomSheet
import com.hinnka.mycamera.ui.components.RawDcpLensOption
import com.hinnka.mycamera.ui.components.RawRenderingEngineSettingsPanel
import com.hinnka.mycamera.video.*
import com.hinnka.mycamera.video.VideoCodec
import com.hinnka.mycamera.ui.icons.AppIcons

private enum class VideoSettingPanel {
    ASPECT_RATIO,
    LOG_PROFILE,
    BITRATE,
    CODEC,
    MICROPHONE
}

private val CameraTopSheetContentTopPadding = 32.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraTopSheet(
    visible: Boolean,
    captureMode: CaptureMode,
    aspectRatio: AspectRatio,
    topSheetAspectRatios: List<AspectRatio>,
    onAspectRatioChange: (AspectRatio) -> Unit,
    videoAspectRatio: VideoAspectRatio,
    onVideoAspectRatioChange: (VideoAspectRatio) -> Unit,
    videoLogProfile: VideoLogProfile,
    availableVideoLogProfiles: List<VideoLogProfile>,
    onVideoLogProfileChange: (VideoLogProfile) -> Unit,
    videoBitrate: VideoBitratePreset,
    onVideoBitrateChange: (VideoBitratePreset) -> Unit,
    videoCodec: VideoCodec,
    onVideoCodecChange: (VideoCodec) -> Unit,
    videoAudioInputId: String,
    videoAudioInputOptions: List<VideoAudioInputOption>,
    onVideoAudioInputChange: (String) -> Unit,
    useRaw: Boolean,
    isRawSupported: Boolean,
    rawDcpId: String?,
    rawDcpIdsByLens: Map<String, String?> = emptyMap(),
    rawDcpLensOptions: List<RawDcpLensOption> = emptyList(),
    availableDcps: List<DcpInfo>,
    rawHncsFilmCurveMode: HncsFilmCurveMode,
    rawRenderingEngine: RawRenderingEngine,
    rawToneMappingParameters: RawToneMappingParameters,
    rawSpectralFilmSelection: SpectralFilmSelection?,
    rawSpectralFilmPrint: String?,
    ultraHdrEnabled: Boolean,
    onUltraHdrToggle: (Boolean) -> Unit,
    photoPreviewStabilizationEnabled: Boolean,
    photoPreviewStabilizationAvailable: Boolean,
    onPhotoPreviewStabilizationChange: (Boolean) -> Unit,
    onRawDcpChange: (String?) -> Unit,
    onRawDcpIdsByLensChange: ((Map<String, String?>) -> Unit)? = null,
    onRawHncsFilmCurveModeChange: (HncsFilmCurveMode) -> Unit,
    onImportRawDcp: () -> Unit,
    onDeleteRawDcp: (DcpInfo) -> Unit,
    onRawColorEngineChange: (RawRenderingEngine) -> Unit,
    onRawToneMappingParametersChange: (RawToneMappingParameters) -> Unit,
    onRawSpectralFilmSelectionChange: (SpectralFilmSelection?) -> Unit,
    onRawSpectralFilmPrintChange: (String?) -> Unit,
    meteringMode: MeteringMode,
    onMeteringModeChange: (MeteringMode) -> Unit,
    captureMegapixels: Int = 12,
    captureSize: android.util.Size? = null,
    resolutionDiagnostic: com.hinnka.mycamera.camera.CameraUtils.ResolutionDiagnostic? = null,
    availableMegapixels: List<Int> = emptyList(),
    onCaptureMegapixelsChange: (Int) -> Unit = {},
    fusionScale: Float = 1f,
    onFusionScaleChange: (Float) -> Unit = {},
    onFilterManageClick: () -> Unit,
    onFrameManageClick: () -> Unit,
    onPresetManageClick: () -> Unit,
    onToolboxClick: () -> Unit,
    onMoreSettingsClick: () -> Unit,
    useJpgMax: Boolean,
    onJpgMaxToggle: (Boolean) -> Unit,
    useMultipleExposure: Boolean,
    onMultipleExposureToggle: (Boolean) -> Unit,
    contentTopPadding: Dp = CameraTopSheetContentTopPadding,
    modifier: Modifier = Modifier
) {
    var expandedVideoPanel by rememberSaveable { mutableStateOf<VideoSettingPanel?>(null) }
    var showRenderingEngineSheet by rememberSaveable { mutableStateOf(false) }
    var showContentManagementOptions by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
    var quickInfo by remember { mutableStateOf<QuickInfo?>(null) }
    fun handleContentManagementAction(action: () -> Unit) {
        showContentManagementOptions = false
        action()
    }

    LaunchedEffect(visible, captureMode) {
        showContentManagementOptions = false
    }

    LaunchedEffect(useRaw) {
        if (!useRaw) {
            showRenderingEngineSheet = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                // Keep the screen's top inset fixed, but measure content and side padding
                // in the rotated viewport so landscape controls stay inside the sheet.
                .padding(top = contentTopPadding)
                .autoRotate(matchParentSize = true)
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (captureMode == CaptureMode.PHOTO) {
                    val isProfessionalMode = useRaw && isRawSupported
                    SectionLabel(title = stringResource(R.string.aspect_ratio))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatio.sanitizeTopSheetRatios(topSheetAspectRatios).forEach { ratio ->
                            val isSelected = aspectRatio == ratio
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) AccentColor else Color.White.copy(
                                            alpha = 0.12f
                                        )
                                    )
                                    .clickable { onAspectRatioChange(ratio) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ratio.getDisplayName(),
                                    color = if (isSelected) OnAccentColor else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Réglages courants en haut ; le reste est replié dans « Avancé ».
                    QuickSettingGrid { itemModifier ->
                        if (isProfessionalMode) {
                            QuickSettingButton2(
                                title = stringResource(R.string.settings_raw_color_engine),
                                checked = true,
                                onClick = { showRenderingEngineSheet = true },
                                onInfoClick = { quickInfo = QuickInfo(R.string.settings_raw_color_engine, R.string.quick_info_render_engine) },
                                modifier = itemModifier
                            )
                        } else {
                            QuickSettingToggle(
                                title = stringResource(R.string.settings_use_jpg_max),
                                checked = useJpgMax,
                                onCheckedChange = onJpgMaxToggle,
                                onInfoClick = { quickInfo = QuickInfo(R.string.settings_use_jpg_max, R.string.quick_info_denoise) },
                                modifier = itemModifier
                            )
                        }
                        if (!isProfessionalMode) {
                            MegapixelsQuickSetting(
                                availableMegapixels = availableMegapixels,
                                captureMegapixels = captureMegapixels,
                                captureSize = captureSize,
                                diagnostic = resolutionDiagnostic,
                                fusionActive = useJpgMax,
                                fusionScale = fusionScale,
                                onNativeChange = onCaptureMegapixelsChange,
                                onFusionChange = { scale ->
                                    // La fusion multi-image n'agit que si la réduction du bruit multi-image est active.
                                    if (!useJpgMax) onJpgMaxToggle(true)
                                    onFusionScaleChange(scale)
                                },
                                onInfoClick = { quickInfo = QuickInfo(R.string.quick_megapixels_title, R.string.quick_info_megapixels) },
                                modifier = itemModifier
                            )
                        }
                        ToolboxQuickSetting(
                            onToolboxClick = onToolboxClick,
                            onInfoClick = { quickInfo = QuickInfo(R.string.toolbox_title, R.string.quick_info_toolbox) },
                            modifier = itemModifier
                        )

                        ContentManagementQuickSetting(
                            onClick = { showContentManagementOptions = !showContentManagementOptions },
                            onInfoClick = { quickInfo = QuickInfo(R.string.settings_section_management, R.string.quick_info_management) },
                            modifier = itemModifier
                        )
                    }

                    ContentManagementOptionsPanel(
                        visible = showContentManagementOptions,
                        onFilterManageClick = {
                            handleContentManagementAction(onFilterManageClick)
                        },
                        onFrameManageClick = {
                            handleContentManagementAction(onFrameManageClick)
                        },
                        onPresetManageClick = {
                            handleContentManagementAction(onPresetManageClick)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AdvancedSectionHeader(
                        expanded = showAdvancedSettings,
                        onClick = { showAdvancedSettings = !showAdvancedSettings }
                    )

                    AnimatedVisibility(
                        visible = showAdvancedSettings,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            QuickSettingGrid { itemModifier ->
                                if (!isProfessionalMode) {
                                    QuickSettingToggle(
                                        title = stringResource(R.string.settings_use_multiple_exposure),
                                        checked = useMultipleExposure,
                                        onCheckedChange = onMultipleExposureToggle,
                                        onInfoClick = { quickInfo = QuickInfo(R.string.settings_use_multiple_exposure, R.string.quick_info_multiple_exposure) },
                                        modifier = itemModifier
                                    )
                                }
                                QuickSettingToggle(
                                    title = stringResource(R.string.settings_ultra_hdr_gain_map),
                                    checked = ultraHdrEnabled,
                                    onCheckedChange = onUltraHdrToggle,
                                    onInfoClick = { quickInfo = QuickInfo(R.string.settings_ultra_hdr_gain_map, R.string.quick_info_ultra_hdr) },
                                    modifier = itemModifier
                                )
                                QuickSettingToggle(
                                    title = stringResource(R.string.camera_preview_stabilization),
                                    checked = photoPreviewStabilizationEnabled,
                                    onCheckedChange = onPhotoPreviewStabilizationChange,
                                    enabled = photoPreviewStabilizationAvailable,
                                    onInfoClick = { quickInfo = QuickInfo(R.string.camera_preview_stabilization, R.string.quick_info_stabilization) },
                                    modifier = itemModifier
                                )
                                MeteringModeQuickSetting(
                                    meteringMode = meteringMode,
                                    onMeteringModeChange = onMeteringModeChange,
                                    onInfoClick = { quickInfo = QuickInfo(R.string.metering_mode, R.string.quick_info_metering) },
                                    modifier = itemModifier
                                )
                            }
                        }
                    }
                } else {
                    SectionLabel(title = stringResource(R.string.video_aspect_chip))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VideoAspectRatio.entries.forEach { ratio ->
                            val isSelected = videoAspectRatio == ratio
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) AccentColor else Color.White.copy(
                                            alpha = 0.12f
                                        )
                                    )
                                    .clickable { onVideoAspectRatioChange(ratio) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = videoAspectRatioLabel(ratio),
                                    color = if (isSelected) OnAccentColor else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (videoAspectRatio == VideoAspectRatio.OPEN_GATE) {
                        Text(
                            text = stringResource(R.string.video_open_gate_resolution_description),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    QuickSettingGrid(columns = 2, itemHeight = 40.dp) { itemModifier ->
                        VideoSettingTile(
                            title = stringResource(R.string.video_log_chip),
                            summary = videoLogProfileLabel(videoLogProfile),
                            expanded = expandedVideoPanel == VideoSettingPanel.LOG_PROFILE,
                            onClick = {
                                expandedVideoPanel = if (expandedVideoPanel == VideoSettingPanel.LOG_PROFILE) null else VideoSettingPanel.LOG_PROFILE
                            },
                            modifier = itemModifier
                        )
                        VideoSettingTile(
                            title = stringResource(R.string.video_bitrate_chip),
                            summary = "${videoBitrate.bitrateMbps}M",
                            expanded = expandedVideoPanel == VideoSettingPanel.BITRATE,
                            onClick = {
                                expandedVideoPanel = if (expandedVideoPanel == VideoSettingPanel.BITRATE) null else VideoSettingPanel.BITRATE
                            },
                            modifier = itemModifier
                        )
                        VideoSettingTile(
                            title = stringResource(R.string.video_codec_chip),
                            summary = videoCodec.displayName,
                            expanded = expandedVideoPanel == VideoSettingPanel.CODEC,
                            onClick = {
                                expandedVideoPanel = if (expandedVideoPanel == VideoSettingPanel.CODEC) null else VideoSettingPanel.CODEC
                            },
                            modifier = itemModifier
                        )
                        VideoSettingTile(
                            title = stringResource(R.string.video_microphone_title),
                            summary = selectedVideoAudioInputLabel(
                                selectedAudioInputId = videoAudioInputId,
                                options = videoAudioInputOptions
                            ),
                            expanded = expandedVideoPanel == VideoSettingPanel.MICROPHONE,
                            onClick = {
                                expandedVideoPanel = if (expandedVideoPanel == VideoSettingPanel.MICROPHONE) null else VideoSettingPanel.MICROPHONE
                            },
                            modifier = itemModifier
                        )
                    }

                    AnimatedVisibility(
                        visible = expandedVideoPanel != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            VideoSettingExpandedPanel {
                                when (expandedVideoPanel) {
                                    VideoSettingPanel.LOG_PROFILE -> {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            availableVideoLogProfiles.forEach { profile ->
                                                val isSelected = videoLogProfile == profile
                                                VideoOptionChip(
                                                    title = videoLogProfileLabel(profile),
                                                    selected = isSelected,
                                                    onClick = { onVideoLogProfileChange(profile) }
                                                )
                                            }
                                        }
                                    }

                                    VideoSettingPanel.BITRATE -> {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            VideoBitratePreset.entries.forEach { bitrate ->
                                                VideoOptionChip(
                                                    title = "${bitrate.bitrateMbps}M",
                                                    selected = videoBitrate == bitrate,
                                                    onClick = { onVideoBitrateChange(bitrate) }
                                                )
                                            }
                                        }
                                    }

                                    VideoSettingPanel.CODEC -> {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            VideoCodec.entries.forEach { codec ->
                                                VideoOptionChip(
                                                    title = codec.displayName,
                                                    selected = videoCodec == codec,
                                                    onClick = { onVideoCodecChange(codec) }
                                                )
                                            }
                                        }
                                    }

                                    VideoSettingPanel.MICROPHONE -> {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            VideoOptionChip(
                                                title = stringResource(R.string.video_microphone_auto),
                                                selected = videoAudioInputId == VIDEO_AUDIO_INPUT_AUTO,
                                                onClick = { onVideoAudioInputChange(VIDEO_AUDIO_INPUT_AUTO) }
                                            )
                                            videoAudioInputOptions.forEach { option ->
                                                VideoOptionChip(
                                                    title = videoAudioInputLabel(option),
                                                    selected = videoAudioInputId == option.id,
                                                    onClick = { onVideoAudioInputChange(option.id) }
                                                )
                                            }
                                        }
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }
                }

                if (captureMode == CaptureMode.VIDEO) {
                    Spacer(modifier = Modifier.height(16.dp))

                    ContentManagementQuickSetting(
                        onClick = { showContentManagementOptions = !showContentManagementOptions },
                        onInfoClick = { quickInfo = QuickInfo(R.string.settings_section_management, R.string.quick_info_management) },
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )

                    ContentManagementOptionsPanel(
                        visible = showContentManagementOptions,
                        onFilterManageClick = {
                            handleContentManagementAction(onFilterManageClick)
                        },
                        onFrameManageClick = {
                            handleContentManagementAction(onFrameManageClick)
                        },
                        onPresetManageClick = {
                            handleContentManagementAction(onPresetManageClick)
                        }
                    )
                }

                // More Settings Button
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onMoreSettingsClick)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color = Color.White.copy(alpha = 0.15f))
                            .padding(16.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    quickInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { quickInfo = null },
            title = { Text(text = stringResource(info.titleRes)) },
            text = { Text(text = stringResource(info.bodyRes)) },
            confirmButton = {
                TextButton(onClick = { quickInfo = null }) {
                    Text(text = stringResource(R.string.close))
                }
            }
        )
    }

    if (showRenderingEngineSheet) {
        AppModalBottomSheet(
            onDismissRequest = { showRenderingEngineSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            Text(
                text = stringResource(R.string.settings_raw_color_engine),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // Fill the sheet so switching engines only changes the scroll range, never the anchors.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                RawRenderingEngineSettingsPanel(
                    selectedDcpId = rawDcpId,
                    rawDcpIdsByLens = rawDcpIdsByLens,
                    dcpLensOptions = rawDcpLensOptions,
                    availableDcps = availableDcps,
                    rawRenderingEngine = rawRenderingEngine,
                    rawToneMappingParameters = rawToneMappingParameters,
                    spectralFilmSelection = rawSpectralFilmSelection,
                    spectralFilmPrint = rawSpectralFilmPrint,
                    onSelectDcp = onRawDcpChange,
                    onRawDcpIdsByLensChange = onRawDcpIdsByLensChange,
                    onImportDcp = onImportRawDcp,
                    onDeleteDcp = onDeleteRawDcp,
                    hncsFilmCurveMode = rawHncsFilmCurveMode,
                    onHncsFilmCurveModeChange = onRawHncsFilmCurveModeChange,
                    onRawColorEngineChange = onRawColorEngineChange,
                    onRawToneMappingParametersChange = onRawToneMappingParametersChange,
                    onSpectralFilmSelectionChange = onRawSpectralFilmSelectionChange,
                    onSpectralFilmPrintChange = onRawSpectralFilmPrintChange,
                    inlineEngineOptions = true,
                    showToneMappingControls = true,
                )
            }
        }
    }
}

@Composable
private fun VideoSettingTile(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (expanded) AccentColor else Color.White.copy(alpha = 0.14f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = title,
            color = if (expanded) OnAccentColor.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.72f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickSettingLabel(
                text = summary,
                color = if (expanded) OnAccentColor else Color.White,
                maxFontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.OpenInFull,
                contentDescription = null,
                tint = if (expanded) OnAccentColor else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun VideoSettingExpandedPanel(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun VideoOptionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) AccentColor else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) OnAccentColor else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun selectedVideoAudioInputLabel(
    selectedAudioInputId: String,
    options: List<VideoAudioInputOption>
): String {
    if (selectedAudioInputId == VIDEO_AUDIO_INPUT_AUTO) {
        return stringResource(R.string.video_microphone_auto)
    }
    val option = options.firstOrNull { it.id == selectedAudioInputId }
    return option?.let { videoAudioInputLabel(it) } ?: stringResource(R.string.video_microphone_disconnected)
}

@Composable
private fun videoAudioInputLabel(option: VideoAudioInputOption): String {
    val baseLabel = when (option.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> stringResource(R.string.video_microphone_builtin)
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> stringResource(R.string.video_microphone_wired)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> stringResource(R.string.video_microphone_bluetooth)
        AudioDeviceInfo.TYPE_BLE_HEADSET -> stringResource(R.string.video_microphone_ble)
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> stringResource(R.string.video_microphone_usb)
        AudioDeviceInfo.TYPE_HDMI -> stringResource(R.string.video_microphone_hdmi)
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> stringResource(R.string.video_microphone_line_in)
        else -> stringResource(R.string.video_microphone_external)
    }
    val customName = option.productName
        ?.takeUnless { it.equals(Build.MODEL, ignoreCase = true) }
        ?.takeUnless { it.equals(Build.DEVICE, ignoreCase = true) }
        ?.takeUnless { it.equals(Build.PRODUCT, ignoreCase = true) }
    val suffix = when {
        option.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && !option.address.isNullOrBlank() -> option.address
        customName != null && customName != baseLabel -> customName
        else -> null
    }
    return if (suffix != null) "$baseLabel (${suffix})" else baseLabel
}

@Composable
private fun videoAspectRatioLabel(aspectRatio: VideoAspectRatio): String {
    return when (aspectRatio) {
        VideoAspectRatio.RATIO_16_9 -> stringResource(R.string.video_aspect_16_9)
        VideoAspectRatio.RATIO_21_9 -> stringResource(R.string.video_aspect_21_9)
        VideoAspectRatio.OPEN_GATE -> stringResource(R.string.video_aspect_open_gate)
    }
}

@Composable
private fun videoLogProfileLabel(profile: VideoLogProfile): String {
    return when (profile) {
        VideoLogProfile.OFF -> stringResource(R.string.video_log_off)
        else -> profile.displayName
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun MeteringModeQuickSetting(
    meteringMode: MeteringMode,
    onMeteringModeChange: (MeteringMode) -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val meteringLabel = when (meteringMode) {
        MeteringMode.SPOT -> stringResource(R.string.metering_spot)
        MeteringMode.CENTER_WEIGHTED -> stringResource(R.string.metering_center_weighted)
        MeteringMode.SYSTEM_DEFAULT -> stringResource(R.string.metering_system_default)
        MeteringMode.AVERAGE -> stringResource(R.string.metering_average)
        MeteringMode.HIGHLIGHT_PRIORITY -> stringResource(R.string.metering_highlight_priority)
    }
    QuickSettingValue(
        title = stringResource(R.string.metering_mode),
        value = meteringLabel,
        onClick = {
            val next = when (meteringMode) {
                MeteringMode.SPOT -> MeteringMode.SYSTEM_DEFAULT
                MeteringMode.SYSTEM_DEFAULT -> MeteringMode.CENTER_WEIGHTED
                MeteringMode.CENTER_WEIGHTED -> MeteringMode.AVERAGE
                MeteringMode.AVERAGE -> MeteringMode.HIGHLIGHT_PRIORITY
                MeteringMode.HIGHLIGHT_PRIORITY -> MeteringMode.SPOT
            }
            onMeteringModeChange(next)
        },
        onInfoClick = onInfoClick,
        modifier = modifier
    )
}

/** Une définition proposée : native (taille du capteur) ou obtenue par fusion multi-image + agrandissement. */
private data class MegapixelOption(val megapixels: Int, val isNative: Boolean, val fusionScale: Float)

private fun buildMegapixelOptions(native: List<Int>, nativeMegapixels: Double): List<MegapixelOption> {
    val options = native.map { MegapixelOption(it, true, 1f) }.toMutableList()
    val maxNative = native.maxOrNull() ?: 0
    if (nativeMegapixels > 0) {
        com.hinnka.mycamera.camera.CameraUtils.MegapixelChoices
            .filter { it > maxNative && it > nativeMegapixels * 1.3 }
            .forEach { target ->
                val scale = kotlin.math.sqrt(target / nativeMegapixels).toFloat()
                    .coerceIn(1f, com.hinnka.mycamera.camera.MultiFrameConfig.MAX_OUTPUT_SCALE)
                options += MegapixelOption(target, false, scale)
            }
    }
    return options
}

@Composable
private fun MegapixelsQuickSetting(
    availableMegapixels: List<Int>,
    captureMegapixels: Int,
    captureSize: android.util.Size?,
    diagnostic: com.hinnka.mycamera.camera.CameraUtils.ResolutionDiagnostic?,
    fusionActive: Boolean,
    fusionScale: Float,
    onNativeChange: (Int) -> Unit,
    onFusionChange: (Float) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nativeMegapixels = captureSize?.let { it.width.toLong() * it.height / 1_000_000.0 } ?: 0.0
    val options = remember(availableMegapixels, nativeMegapixels) {
        buildMegapixelOptions(availableMegapixels, nativeMegapixels)
    }
    val nativeSelected = availableMegapixels.lastOrNull { it <= captureMegapixels }
        ?: availableMegapixels.firstOrNull()
    val fusionSelected: MegapixelOption? = if (fusionActive && fusionScale > 1.05f) {
        options.filter { !it.isNative }.minByOrNull { kotlin.math.abs(it.fusionScale - fusionScale) }
    } else {
        null
    }
    val nativeOption = options.firstOrNull { it.isNative && it.megapixels == nativeSelected }
    val selected: MegapixelOption? = fusionSelected ?: nativeOption ?: options.firstOrNull()
    var showDiagnostic by remember { mutableStateOf(false) }
    if (showDiagnostic) {
        AlertDialog(
            onDismissRequest = { showDiagnostic = false },
            title = { Text(text = stringResource(R.string.quick_megapixels_diag_title)) },
            text = {
                Text(
                    text = resolutionDiagnosticText(captureSize, diagnostic),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostic = false }) {
                    Text(text = stringResource(R.string.close))
                }
            }
        )
    }
    QuickSettingValue(
        title = stringResource(R.string.quick_megapixels_title),
        value = selected?.let {
            val label = stringResource(R.string.capture_megapixels_label, it.megapixels)
            when {
                // Obtenu par fusion de plusieurs photos puis agrandissement (pas la taille native du capteur).
                !it.isNative -> "$label · ${stringResource(R.string.quick_megapixels_fusion)}"
                // Taille réellement utilisée : permet de vérifier que le changement est pris en compte.
                else -> captureSize?.let { size -> "$label · ${size.width}×${size.height}" } ?: label
            }
        } ?: stringResource(R.string.quick_megapixels_unavailable),
        onClick = {
            if (selected != null && options.size > 1) {
                val next = options[(options.indexOf(selected) + 1) % options.size]
                if (next.isNative) {
                    onFusionChange(1f)
                    onNativeChange(next.megapixels)
                } else {
                    onFusionChange(next.fusionScale)
                }
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (next.isNative) R.string.quick_megapixels_switching else R.string.quick_megapixels_switching_fusion,
                        next.megapixels
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                // Explique pourquoi rien ne change au lieu de rester silencieux.
                showDiagnostic = true
            }
        },
        onInfoClick = onInfoClick,
        modifier = modifier
    )
}

@Composable
private fun ToolboxQuickSetting(
    onToolboxClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    QuickSettingButton(
        title = stringResource(R.string.toolbox_title),
        icon = AppIcons.Palette,
        onClick = onToolboxClick,
        onInfoClick = onInfoClick,
        modifier = modifier
    )
}

@Composable
private fun ContentManagementQuickSetting(
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    QuickSettingButton(
        title = stringResource(R.string.settings_section_management),
        icon = AppIcons.Tune,
        onClick = onClick,
        onInfoClick = onInfoClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSettingGrid(
    columns: Int = 3,
    itemHeight: Dp = 48.dp,
    content: @Composable (Modifier) -> Unit
) {
    // Keep a consistent column count as the available photo controls change.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = columns
    ) {
        content(Modifier.weight(1f).height(itemHeight))
    }
}

@Composable
private fun QuickSettingLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    maxFontSize: TextUnit = 10.sp
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.copy(
            color = color,
            fontWeight = fontWeight,
            fontSize = maxFontSize,
            lineHeight = 1.2.em
        ),
        maxLines = 2,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = maxFontSize)
    )
}

@Composable
private fun ContentManagementOptionsPanel(
    visible: Boolean,
    onFilterManageClick: () -> Unit,
    onFrameManageClick: () -> Unit,
    onPresetManageClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            QuickSettingGrid { itemModifier ->
                QuickSettingButton(
                    title = stringResource(R.string.settings_filter_management),
                    icon = AppIcons.AutoAwesome,
                    onClick = onFilterManageClick,
                    modifier = itemModifier
                )
                QuickSettingButton(
                    title = stringResource(R.string.settings_frame_management),
                    icon = AppIcons.BorderBottom,
                    onClick = onFrameManageClick,
                    modifier = itemModifier
                )
                QuickSettingButton(
                    title = stringResource(R.string.settings_preset_management),
                    icon = AppIcons.Bookmark,
                    onClick = onPresetManageClick,
                    modifier = itemModifier
                )
            }
        }
    }
}

@Composable
fun QuickSettingValue(
    title: String,
    value: String,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
                QuickSettingLabel(
                    text = value,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            onInfoClick?.let { QuickInfoButton(onClick = it, tint = Color.White.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
fun QuickSettingButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickSettingLabel(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            onInfoClick?.let { QuickInfoButton(onClick = it, tint = Color.White.copy(alpha = 0.6f)) }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}



@Composable
fun QuickSettingButton2(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (checked) AccentColor else Color.White.copy(
                    alpha = 0.15f
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickSettingLabel(
                text = title,
                color = if (checked) OnAccentColor else Color.White.copy(alpha = 0.9f),
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            onInfoClick?.let {
                QuickInfoButton(
                    onClick = it,
                    tint = if (checked) OnAccentColor else Color.White.copy(alpha = 0.9f)
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = if (checked) OnAccentColor else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun QuickSettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (checked) AccentColor.copy(alpha = contentAlpha) else Color.White.copy(
                    alpha = 0.15f
                )
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickSettingLabel(
                text = title,
                color = if (checked) {
                    OnAccentColor.copy(alpha = contentAlpha)
                } else {
                    Color.White.copy(alpha = 0.9f * contentAlpha)
                },
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            onInfoClick?.let {
                QuickInfoButton(
                    onClick = it,
                    tint = if (checked) {
                        OnAccentColor.copy(alpha = contentAlpha)
                    } else {
                        Color.White.copy(alpha = 0.6f * contentAlpha)
                    }
                )
            }

            // Simple indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (checked) {
                            OnAccentColor.copy(alpha = contentAlpha)
                        } else {
                            Color.White.copy(alpha = 0.2f * contentAlpha)
                        }
                    )
            )
        }
    }
}

/** Texte d'aide affiché quand on touche le « i » d'une tuile. */
private data class QuickInfo(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

@Composable
private fun QuickInfoButton(
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.quick_info_button),
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun AdvancedSectionHeader(
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_group_advanced),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun resolutionDiagnosticText(
    captureSize: android.util.Size?,
    d: com.hinnka.mycamera.camera.CameraUtils.ResolutionDiagnostic?
): String {
    val noneLabel = stringResource(R.string.quick_megapixels_diag_none)
    fun android.util.Size?.label(): String = this?.let {
        val mp = it.width.toLong() * it.height / 1_000_000.0
        "${it.width}×${it.height} (${"%.1f".format(mp)} Mpx)"
    } ?: noneLabel
    fun android.util.Size?.pixels() = this?.let { it.width.toLong() * it.height } ?: 0L
    if (d == null) return stringResource(R.string.quick_megapixels_single, captureSize.label())
    val current = captureSize.pixels()
    val conclusion = when {
        d.ultraHighResCapability && d.maxResolutionModeMax.pixels() > current * 1.5 ->
            stringResource(R.string.quick_megapixels_diag_cause_max_mode, d.maxResolutionModeMax.label())
        d.vendorMax.pixels() > current * 1.5 ->
            stringResource(R.string.quick_megapixels_diag_cause_vendor, d.vendorMax.label())
        d.jpegMax.pixels() > current * 1.5 ->
            stringResource(R.string.quick_megapixels_diag_cause_jpeg_only, d.jpegMax.label())
        else -> stringResource(R.string.quick_megapixels_diag_cause_binning)
    }
    return stringResource(
        R.string.quick_megapixels_diag_body,
        d.pixelArray.label(),
        captureSize.label(),
        d.yuvHighResMax.label(),
        d.jpegMax.label(),
        stringResource(
            if (d.ultraHighResCapability) R.string.quick_megapixels_diag_yes else R.string.quick_megapixels_diag_no
        ),
        d.maxResolutionModeMax.label(),
        d.vendorMax.label(),
        conclusion
    )
}
