package com.hinnka.mycamera.ui.gallery

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.repeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowInsets
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.theme.AccentColor

object EditLayoutConstants {
    val PORTRAIT_TOOLBAR_WIDTH = 56.dp
    val PORTRAIT_SETTINGS_PANEL_WIDTH = 300.dp
    val LANDSCAPE_TOOL_PANEL_WIDTH = 200.dp
    val LANDSCAPE_SETTINGS_PANEL_WIDTH = 300.dp
    val TAB_BAR_HEIGHT = 48.dp
    val TOOL_ICON_SIZE = 48.dp
    val PANEL_CORNER_RADIUS = 12.dp
    val PANEL_ELEVATION = 8.dp
    val TOOLBAR_PADDING = 8.dp
}

@Composable
fun EditToolIcon(
    icon: ImageVector, 
    label: String, 
    isSelected: Boolean, 
    onClick: () -> Unit, 
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) AccentColor.copy(alpha = 0.2f) else Color.Transparent
    val borderColor = if (isSelected) AccentColor else Color.White.copy(alpha = 0.3f)
    val iconTint = if (isSelected) AccentColor else Color.White.copy(alpha = 0.7f)
    
    Box(
        modifier = modifier
            .size(EditLayoutConstants.TOOL_ICON_SIZE)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = label, 
            tint = iconTint, 
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SamsungEditTabItem(
    title: String, 
    isSelected: Boolean, 
    onClick: () -> Unit, 
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "$title tab"
            }
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 2.dp)
                .background(if (isSelected) AccentColor else Color.Transparent)
        )
    }
}

@Composable
fun SettingsPanelHeader(
    title: String, 
    isCollapsed: Boolean, 
    onToggleCollapse: () -> Unit, 
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onToggleCollapse),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp)
        )
        Icon(
            imageVector = if (isCollapsed) AppIcons.ExpandMore else AppIcons.ExpandLess,
            contentDescription = if (isCollapsed) "Expand" else "Collapse",
            tint = Color.White.copy(alpha = 0.82f),
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp)
        )
    }
}

@Composable
fun SamsungEditLayout(
    topBar: @Composable () -> Unit,
    imageArea: @Composable () -> Unit,
    toolPanel: @Composable () -> Unit,
    settingsPanel: @Composable () -> Unit,
    tabBar: @Composable () -> Unit,
    isSettingsPanelVisible: Boolean,
    isSettingsPanelCollapsed: Boolean,
    modifier: Modifier = Modifier
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    
    if (isPortrait) {
        PortraitEditLayout(
            topBar = topBar,
            imageArea = imageArea,
            toolPanel = toolPanel,
            settingsPanel = settingsPanel,
            tabBar = tabBar,
            isSettingsPanelVisible = isSettingsPanelVisible,
            isSettingsPanelCollapsed = isSettingsPanelCollapsed,
            modifier = modifier
        )
    } else {
        LandscapeEditLayout(
            topBar = topBar,
            imageArea = imageArea,
            toolPanel = toolPanel,
            settingsPanel = settingsPanel,
            tabBar = tabBar,
            isSettingsPanelVisible = isSettingsPanelVisible,
            isSettingsPanelCollapsed = isSettingsPanelCollapsed,
            modifier = modifier
        )
    }
}

@Composable
fun PortraitEditLayout(
    topBar: @Composable () -> Unit,
    imageArea: @Composable () -> Unit,
    toolPanel: @Composable () -> Unit,
    settingsPanel: @Composable () -> Unit,
    tabBar: @Composable () -> Unit,
    isSettingsPanelVisible: Boolean,
    isSettingsPanelCollapsed: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar at the top
        topBar()
        
        // Main content area with image and side panels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Image area - takes remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                imageArea()
            }
            
            // Tool panel on the right
            Column(
                modifier = Modifier
                    .width(EditLayoutConstants.PORTRAIT_TOOLBAR_WIDTH)
                    .fillMaxHeight()
                    .padding(vertical = EditLayoutConstants.TOOLBAR_PADDING),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                toolPanel()
            }
            
            // Settings panel - slides from right
            if (isSettingsPanelVisible) {
                val panelWidth = if (isSettingsPanelCollapsed) {
                    EditLayoutConstants.PORTRAIT_TOOLBAR_WIDTH
                } else {
                    EditLayoutConstants.PORTRAIT_SETTINGS_PANEL_WIDTH
                }
                
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .padding(
                            top = EditLayoutConstants.TOOLBAR_PADDING,
                            bottom = EditLayoutConstants.TOOLBAR_PADDING
                        )
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(EditLayoutConstants.PANEL_CORNER_RADIUS),
                        shadowElevation = EditLayoutConstants.PANEL_ELEVATION,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        settingsPanel()
                    }
                }
            }
        }
        
        // Tab bar at the bottom
        Box(
            modifier = Modifier
                .height(EditLayoutConstants.TAB_BAR_HEIGHT)
                .fillMaxWidth()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                shadowElevation = EditLayoutConstants.PANEL_ELEVATION,
                modifier = Modifier.fillMaxSize()
            ) {
                tabBar()
            }
        }
    }
}

@Composable
fun LandscapeEditLayout(
    topBar: @Composable () -> Unit,
    imageArea: @Composable () -> Unit,
    toolPanel: @Composable () -> Unit,
    settingsPanel: @Composable () -> Unit,
    tabBar: @Composable () -> Unit,
    isSettingsPanelVisible: Boolean,
    isSettingsPanelCollapsed: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar at the top
        topBar()
        
        // Tab bar below top bar
        Box(
            modifier = Modifier
                .height(EditLayoutConstants.TAB_BAR_HEIGHT)
                .fillMaxWidth()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                shadowElevation = EditLayoutConstants.PANEL_ELEVATION,
                modifier = Modifier.fillMaxSize()
            ) {
                tabBar()
            }
        }
        
        // Main content area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Tool panel on the left
            Box(
                modifier = Modifier
                    .width(EditLayoutConstants.LANDSCAPE_TOOL_PANEL_WIDTH)
                    .fillMaxHeight()
                    .padding(vertical = EditLayoutConstants.TOOLBAR_PADDING)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(EditLayoutConstants.PANEL_CORNER_RADIUS),
                    shadowElevation = EditLayoutConstants.PANEL_ELEVATION,
                    modifier = Modifier.fillMaxSize()
                ) {
                    toolPanel()
                }
            }
            
            // Image area in the center
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = EditLayoutConstants.TOOLBAR_PADDING)
            ) {
                imageArea()
            }
            
            // Settings panel on the right
            if (isSettingsPanelVisible) {
                val panelWidth = if (isSettingsPanelCollapsed) {
                    EditLayoutConstants.LANDSCAPE_TOOL_PANEL_WIDTH
                } else {
                    EditLayoutConstants.LANDSCAPE_SETTINGS_PANEL_WIDTH
                }
                
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .padding(vertical = EditLayoutConstants.TOOLBAR_PADDING)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(EditLayoutConstants.PANEL_CORNER_RADIUS),
                        shadowElevation = EditLayoutConstants.PANEL_ELEVATION,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        settingsPanel()
                    }
                }
            }
        }
    }
}
