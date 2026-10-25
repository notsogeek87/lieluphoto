package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraInfo
import com.hinnka.mycamera.camera.LensType
import kotlin.math.roundToInt

/**
 * Petit repère indiquant l'objectif actuellement utilisé (type et focale équivalente 35 mm).
 */
@Composable
fun LensIndicator(
    camera: CameraInfo,
    modifier: Modifier = Modifier
) {
    val name = stringResource(
        when (camera.lensType) {
            LensType.FRONT -> R.string.lens_indicator_front
            LensType.BACK_MAIN -> R.string.lens_indicator_main
            LensType.BACK_ULTRA_WIDE -> R.string.lens_indicator_ultra_wide
            LensType.BACK_TELEPHOTO -> R.string.lens_indicator_telephoto
            LensType.BACK_MACRO -> R.string.lens_indicator_macro
        }
    )
    val focalLength = camera.focalLength35mmEquivalent.takeIf { it > 0f }?.roundToInt()
    val label = if (focalLength != null) {
        stringResource(R.string.lens_indicator_format, name, focalLength)
    } else {
        name
    }

    Text(
        text = label,
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 8.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}
