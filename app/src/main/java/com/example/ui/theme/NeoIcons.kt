package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight vector icons for NeoGBC, eliminating the 12MB material-icons-extended dependency
 * while keeping crisp 24dp Material vector icons across all screens.
 */
object NeoIcons {

    private fun buildIcon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Gamepad: ImageVector = buildIcon("Gamepad") {
        path(fill = SolidColor(Color.White)) {
            moveTo(21f, 6f)
            horizontalLineTo(3f)
            curveTo(1.9f, 6f, 1f, 6.9f, 1f, 8f)
            verticalLineToRelative(8f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(18f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(11f, 13f)
            horizontalLineTo(8f)
            verticalLineToRelative(3f)
            horizontalLineTo(6f)
            verticalLineToRelative(-3f)
            horizontalLineTo(3f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(3f)
            verticalLineTo(8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(2f)
            close()
            moveTo(15.5f, 15f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
            reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
            reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
            close()
            moveTo(19.5f, 12f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
            reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
            reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
            close()
        }
    }

    val CreateNewFolder: ImageVector = buildIcon("CreateNewFolder") {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 6f)
            horizontalLineToRelative(-8f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(4f)
            curveToRelative(-1.11f, 0f, -1.99f, 0.89f, -1.99f, 2f)
            lineTo(2f, 18f)
            curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
            verticalLineTo(8f)
            curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
            close()
            moveTo(19f, 14f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(3f)
            verticalLineTo(9f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val Folder: ImageVector = buildIcon("Folder") {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 4f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(2f, 18f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-8f)
            lineToRelative(-2f, -2f)
            close()
        }
    }

    val FileOpen: ImageVector = buildIcon("FileOpen") {
        path(fill = SolidColor(Color.White)) {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            lineToRelative(-6f, -6f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
        }
    }

    val AddPhoto: ImageVector = buildIcon("AddPhoto") {
        path(fill = SolidColor(Color.White)) {
            moveTo(21f, 19f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            close()
            moveTo(8.5f, 13.5f)
            lineToRelative(2.5f, 3.01f)
            lineTo(14.5f, 12f)
            lineToRelative(4.5f, 6f)
            horizontalLineTo(5f)
            lineToRelative(3.5f, -4.5f)
            close()
        }
    }

    val AutoAwesome: ImageVector = buildIcon("AutoAwesome") {
        path(fill = SolidColor(Color.White)) {
            moveTo(19f, 9f)
            lineToRelative(1.25f, -2.75f)
            lineTo(23f, 5f)
            lineToRelative(-2.75f, -1.25f)
            lineTo(19f, 1f)
            lineToRelative(-1.25f, 2.75f)
            lineTo(15f, 5f)
            lineToRelative(2.75f, 1.25f)
            lineTo(19f, 9f)
            close()
            moveTo(11.5f, 9.5f)
            lineTo(9f, 4f)
            lineTo(6.5f, 9.5f)
            lineTo(1f, 12f)
            lineToRelative(5.5f, 2.5f)
            lineTo(9f, 20f)
            lineToRelative(2.5f, -5.5f)
            lineTo(17f, 12f)
            lineToRelative(-5.5f, -2.5f)
            close()
        }
    }

    val FastForward: ImageVector = buildIcon("FastForward") {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 18f)
            lineToRelative(8.5f, -6f)
            lineTo(4f, 6f)
            verticalLineToRelative(12f)
            close()
            moveTo(13f, 6f)
            verticalLineToRelative(12f)
            lineToRelative(8.5f, -6f)
            lineTo(13f, 6f)
            close()
        }
    }

    val BookmarkAdd: ImageVector = buildIcon("BookmarkAdd") {
        path(fill = SolidColor(Color.White)) {
            moveTo(17f, 3f)
            horizontalLineTo(7f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            lineToRelative(7f, -3f)
            lineToRelative(7f, 3f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(15f, 11f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
        }
    }

    val Save: ImageVector = buildIcon("Save") {
        path(fill = SolidColor(Color.White)) {
            moveTo(17f, 3f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            lineToRelative(-4f, -4f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
            close()
            moveTo(15f, 9f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(4f)
            close()
        }
    }

    val History: ImageVector = buildIcon("History") {
        path(fill = SolidColor(Color.White)) {
            moveTo(13f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            horizontalLineTo(1f)
            lineToRelative(3.89f, 3.89f)
            lineToRelative(0.07f, 0.14f)
            lineTo(9f, 12f)
            horizontalLineTo(6f)
            curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
            curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
            reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
            close()
            moveTo(12f, 8f)
            verticalLineToRelative(5f)
            lineToRelative(4.28f, 2.54f)
            lineToRelative(0.72f, -1.21f)
            lineToRelative(-3.5f, -2.08f)
            verticalLineTo(8f)
            horizontalLineTo(12f)
            close()
        }
    }

    val VolumeUp: ImageVector = buildIcon("VolumeUp") {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
            close()
            moveTo(14f, 3.23f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
            verticalLineToRelative(2.06f)
            curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
            reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
            close()
        }
    }

    val VolumeOff: ImageVector = buildIcon("VolumeOff") {
        path(fill = SolidColor(Color.White)) {
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(2.21f)
            lineToRelative(2.45f, 2.45f)
            curveToRelative(0.03f, -0.2f, 0.05f, -0.41f, 0.05f, -0.63f)
            close()
            moveTo(19f, 12f)
            curveToRelative(0f, 0.94f, -0.2f, 1.82f, -0.54f, 2.64f)
            lineToRelative(1.51f, 1.51f)
            curveTo(20.63f, 14.91f, 21f, 13.5f, 21f, 12f)
            curveToRelative(0f, -4.28f, -2.99f, -7.86f, -7f, -8.77f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            close()
            moveTo(4.27f, 3f)
            lineTo(3f, 4.27f)
            lineTo(7.73f, 9f)
            horizontalLineTo(3f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineToRelative(-6.73f)
            lineToRelative(4.25f, 4.25f)
            curveToRelative(-0.67f, 0.52f, -1.42f, 0.93f, -2.25f, 1.18f)
            verticalLineToRelative(2.06f)
            curveToRelative(1.38f, -0.31f, 2.63f, -0.95f, 3.69f, -1.81f)
            lineTo(19.73f, 21f)
            lineTo(21f, 19.73f)
            lineToRelative(-9f, -9f)
            lineTo(4.27f, 3f)
            close()
            moveTo(12f, 4f)
            lineTo(9.91f, 6.09f)
            lineTo(12f, 8.18f)
            verticalLineTo(4f)
            close()
        }
    }

    val OpenWith: ImageVector = buildIcon("OpenWith") {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 9f)
            horizontalLineToRelative(4f)
            verticalLineTo(6f)
            horizontalLineToRelative(3f)
            lineToRelative(-5f, -5f)
            lineToRelative(-5f, 5f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(3f)
            close()
            moveTo(9f, 10f)
            horizontalLineTo(6f)
            verticalLineTo(7f)
            lineToRelative(-5f, 5f)
            lineToRelative(5f, 5f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(-4f)
            close()
            moveTo(23f, 12f)
            lineToRelative(-5f, -5f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(3f)
            lineToRelative(5f, -5f)
            close()
            moveTo(14f, 15f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(3f)
            horizontalLineTo(7f)
            lineToRelative(5f, 5f)
            lineToRelative(5f, -5f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(-3f)
            close()
        }
    }

    val Palette: ImageVector = buildIcon("Palette") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
            curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
            curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
            curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
            curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
            horizontalLineTo(16f)
            curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
            curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
            close()
            moveTo(6.5f, 12f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
            reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
            reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
            close()
            moveTo(9.5f, 8f)
            curveTo(8.67f, 8f, 8f, 7.33f, 8f, 6.5f)
            reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
            reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
            reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
            close()
            moveTo(14.5f, 8f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
            reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
            reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
            close()
            moveTo(17.5f, 12f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
            reflectiveCurveTo(19f, 9.67f, 19f, 10.5f)
            reflectiveCurveTo(18.33f, 12f, 17.5f, 12f)
            close()
        }
    }

    val Code: ImageVector = buildIcon("Code") {
        path(fill = SolidColor(Color.White)) {
            moveTo(9.4f, 16.6f)
            lineTo(4.8f, 12f)
            lineToRelative(4.6f, -4.6f)
            lineTo(8f, 6f)
            lineToRelative(-6f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(1.4f, -1.4f)
            close()
            moveTo(14.6f, 16.6f)
            lineToRelative(4.6f, -4.6f)
            lineToRelative(-4.6f, -4.6f)
            lineTo(16f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(-6f, 6f)
            lineToRelative(-1.4f, -1.4f)
            close()
        }
    }

    val ExitToApp: ImageVector = buildIcon("ExitToApp") {
        path(fill = SolidColor(Color.White)) {
            moveTo(10.09f, 15.59f)
            lineTo(11.5f, 17f)
            lineToRelative(5f, -5f)
            lineToRelative(-5f, -5f)
            lineToRelative(-1.41f, 1.41f)
            lineTo(12.67f, 11f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(9.67f)
            lineToRelative(-2.58f, 2.59f)
            close()
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            verticalLineTo(5f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(14f)
            horizontalLineTo(5f)
            verticalLineToRelative(-4f)
            horizontalLineTo(3f)
            verticalLineToRelative(4f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
        }
    }

    val SwapVert: ImageVector = buildIcon("SwapVert") {
        path(fill = SolidColor(Color.White)) {
            moveTo(16f, 17.01f)
            verticalLineTo(10f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(7.01f)
            horizontalLineToRelative(-3f)
            lineTo(15f, 21f)
            lineToRelative(4f, -3.99f)
            horizontalLineToRelative(-3f)
            close()
            moveTo(9f, 3f)
            lineTo(5f, 6.99f)
            horizontalLineToRelative(3f)
            verticalLineTo(14f)
            horizontalLineToRelative(2f)
            verticalLineTo(6.99f)
            horizontalLineToRelative(3f)
            lineTo(9f, 3f)
            close()
        }
    }

    val ExpandLess: ImageVector = buildIcon("ExpandLess") {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 15f)
            lineTo(12f, 9f)
            lineTo(18f, 15f)
        }
    }

    val ExpandMore: ImageVector = buildIcon("ExpandMore") {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }

    val Edit: ImageVector = buildIcon("Edit") {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 17.25f)
            verticalLineTo(21f)
            horizontalLineToRelative(3.75f)
            lineTo(17.81f, 9.94f)
            lineToRelative(-3.75f, -3.75f)
            lineTo(3f, 17.25f)
            close()
            moveTo(20.71f, 7.04f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
            lineToRelative(-2.34f, -2.34f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            lineToRelative(-1.83f, 1.83f)
            lineToRelative(3.75f, 3.75f)
            lineToRelative(1.83f, -1.83f)
            close()
        }
    }

    val Pause: ImageVector = buildIcon("Pause") {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineToRelative(14f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            close()
        }
    }

    val PlayArrow: ImageVector = buildIcon("PlayArrow") {
        path(fill = SolidColor(Color.White)) {
            moveTo(8f, 5f)
            verticalLineToRelative(14f)
            lineToRelative(11f, -7f)
            close()
        }
    }

    val CameraAlt: ImageVector = buildIcon("CameraAlt") {
        path(fill = SolidColor(Color.White)) {
            moveTo(9f, 2f)
            lineTo(7.17f, 4f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-3.17f)
            lineTo(15f, 2f)
            horizontalLineTo(9f)
            close()
            moveTo(12f, 17f)
            curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
            reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
            reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
            reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
            close()
            moveTo(12f, 9f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
            reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
            reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
            close()
        }
    }
}
