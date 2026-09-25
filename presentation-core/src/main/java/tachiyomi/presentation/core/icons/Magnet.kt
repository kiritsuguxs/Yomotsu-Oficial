package tachiyomi.presentation.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("UnusedReceiverParameter")
val CustomIcons.Magnet: ImageVector
    get() {
        if (_Magnet != null) {
            return _Magnet!!
        }
        _Magnet = ImageVector.Builder(
            name = "Magnet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 7f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 4.97f, 4.03f, 9f, 9f, 9f)
                reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                verticalLineTo(7f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 2.76f, -2.24f, 5f, -5f, 5f)
                reflectiveCurveToRelative(-5f, -2.24f, -5f, -5f)
                verticalLineTo(7f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                horizontalLineToRelative(4f)
                verticalLineTo(3f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(17f, 5f)
                horizontalLineToRelative(4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()

        return _Magnet!!
    }

@Suppress("ObjectPropertyName")
private var _Magnet: ImageVector? = null
