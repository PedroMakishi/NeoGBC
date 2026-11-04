package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ControlLayoutEntity
import com.example.ui.theme.AtomicPurple
import com.example.ui.theme.BerryMagenta
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MatrixBlue
import com.example.ui.theme.NeoIcons
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledCardBorder
import com.example.ui.theme.OledSurface
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class EditableControlGroup(val label: String) {
    DPAD("D-Pad"),
    AB_BUTTONS("Botões B / A"),
    START_SELECT("Select / Start"),
    TURBO("Barra Turbo")
}

/**
 * Clamps a control group's center point (in pixels) so that:
 * 1. Its bounding box [center - halfSize .. center + halfSize] NEVER escapes outside the phone screen [0..screenW, 0..screenH].
 * 2. Its bounding box NEVER overlaps into the Game Boy Color display area (`gameRect`).
 * 3. Its bounding box respects the top-left Menu button (`topHudHeightPx`).
 */
fun clampControlCenterOutsideGame(
    desiredCenterPx: Offset,
    halfW: Float,
    halfH: Float,
    screenW: Float,
    screenH: Float,
    gameRect: Rect,
    topHudHeightPx: Float,
    isLandscape: Boolean
): Offset {
    val margin = 4f
    val minScreenX = (halfW + margin).coerceAtMost(screenW / 2f)
    val maxScreenX = (screenW - halfW - margin).coerceAtLeast(minScreenX)
    val maxScreenY = (screenH - halfH - margin).coerceAtLeast(halfH + margin)

    if (isLandscape) {
        val isLeftSide = desiredCenterPx.x < (screenW * 0.5f)
        // In Landscape, the top-left Menu button is only on the Left Pillar; the Right Pillar can use the full height
        val minLandscapeY = if (isLeftSide) {
            (topHudHeightPx + halfH + margin).coerceAtMost(maxScreenY)
        } else {
            (halfH + margin).coerceAtMost(maxScreenY)
        }
        val clampedX = if (isLeftSide) {
            val maxLeftX = (gameRect.left - halfW - margin)
            if (maxLeftX >= minScreenX) {
                desiredCenterPx.x.coerceIn(minScreenX, maxLeftX)
            } else {
                (gameRect.left / 2f).coerceIn(
                    halfW.coerceAtMost(gameRect.left / 2f),
                    (gameRect.left - halfW).coerceAtLeast(0f)
                )
            }
        } else {
            val minRightX = (gameRect.right + halfW + margin)
            if (maxScreenX >= minRightX) {
                desiredCenterPx.x.coerceIn(minRightX, maxScreenX)
            } else {
                ((gameRect.right + screenW) / 2f).coerceIn(gameRect.right, screenW)
            }
        }
        val clampedY = desiredCenterPx.y.coerceIn(minLandscapeY, maxScreenY)
        return Offset(clampedX, clampedY)
    } else {
        val minPortraitY = (gameRect.bottom + halfH + margin).coerceAtMost(maxScreenY)
        val clampedX = desiredCenterPx.x.coerceIn(minScreenX, maxScreenX)
        val clampedY = desiredCenterPx.y.coerceIn(minPortraitY, maxScreenY)
        return Offset(clampedX, clampedY)
    }
}

/**
 * Resolves a control group's center point so that:
 * 1. It obeys `clampControlCenterOutsideGame` (never leaves screen, never enters `gameRect`).
 * 2. Its bounding box [center - halfSize .. center + halfSize] NEVER overlaps any rectangle in `otherControlRects`.
 */
fun resolveNonOverlappingControlCenter(
    desiredCenterPx: Offset,
    fallbackCenterPx: Offset,
    halfW: Float,
    halfH: Float,
    screenW: Float,
    screenH: Float,
    gameRect: Rect,
    topHudHeightPx: Float,
    isLandscape: Boolean,
    otherControlRects: List<Rect>,
    minGapPx: Float = 6f
): Offset {
    fun buildRect(c: Offset): Rect = Rect(
        left = c.x - halfW,
        top = c.y - halfH,
        right = c.x + halfW,
        bottom = c.y + halfH
    )

    fun isRectValid(c: Offset): Boolean {
        val r = buildRect(c)
        val minAllowedTop = if (isLandscape && c.x >= screenW * 0.5f) -0.5f else topHudHeightPx - 0.5f
        if (r.left < -0.5f || r.right > screenW + 0.5f || r.top < minAllowedTop || r.bottom > screenH + 0.5f) {
            return false
        }
        if (r.overlaps(gameRect)) {
            return false
        }
        for (obs in otherControlRects) {
            val inflatedObs = Rect(
                left = obs.left - minGapPx,
                top = obs.top - minGapPx,
                right = obs.right + minGapPx,
                bottom = obs.bottom + minGapPx
            )
            if (r.overlaps(inflatedObs)) {
                return false
            }
        }
        return true
    }

    val primary = clampControlCenterOutsideGame(
        desiredCenterPx = desiredCenterPx,
        halfW = halfW,
        halfH = halfH,
        screenW = screenW,
        screenH = screenH,
        gameRect = gameRect,
        topHudHeightPx = topHudHeightPx,
        isLandscape = isLandscape
    )
    if (isRectValid(primary)) {
        return primary
    }

    val candidates = ArrayList<Offset>(64)

    // 1. Edge-sliding candidates around each obstacle
    val xShifts = ArrayList<Float>()
    val yShifts = ArrayList<Float>()
    xShifts.add(primary.x)
    yShifts.add(primary.y)

    for (obs in otherControlRects) {
        xShifts.add(obs.left - halfW - minGapPx - 1f)
        xShifts.add(obs.right + halfW + minGapPx + 1f)
        yShifts.add(obs.top - halfH - minGapPx - 1f)
        yShifts.add(obs.bottom + halfH + minGapPx + 1f)
    }

    for (cx in xShifts) {
        for (cy in yShifts) {
            val clamped = clampControlCenterOutsideGame(
                desiredCenterPx = Offset(cx, cy),
                halfW = halfW,
                halfH = halfH,
                screenW = screenW,
                screenH = screenH,
                gameRect = gameRect,
                topHudHeightPx = topHudHeightPx,
                isLandscape = isLandscape
            )
            if (isRectValid(clamped)) {
                candidates.add(clamped)
            }
        }
    }

    // 2. Binary search along the segment from fallbackCenterPx to primary so fast drags stop flush at the obstacle border
    val clampedFallback = clampControlCenterOutsideGame(
        desiredCenterPx = fallbackCenterPx,
        halfW = halfW,
        halfH = halfH,
        screenW = screenW,
        screenH = screenH,
        gameRect = gameRect,
        topHudHeightPx = topHudHeightPx,
        isLandscape = isLandscape
    )
    if (isRectValid(clampedFallback)) {
        candidates.add(clampedFallback)
        var low = 0f
        var high = 1f
        var bestT = 0f
        for (step in 0 until 8) {
            val mid = (low + high) * 0.5f
            val interp = Offset(
                clampedFallback.x + (primary.x - clampedFallback.x) * mid,
                clampedFallback.y + (primary.y - clampedFallback.y) * mid
            )
            val clampedInterp = clampControlCenterOutsideGame(
                desiredCenterPx = interp,
                halfW = halfW,
                halfH = halfH,
                screenW = screenW,
                screenH = screenH,
                gameRect = gameRect,
                topHudHeightPx = topHudHeightPx,
                isLandscape = isLandscape
            )
            if (isRectValid(clampedInterp)) {
                bestT = mid
                low = mid
            } else {
                high = mid
            }
        }
        val bestSegmentPt = clampControlCenterOutsideGame(
            desiredCenterPx = Offset(
                clampedFallback.x + (primary.x - clampedFallback.x) * bestT,
                clampedFallback.y + (primary.y - clampedFallback.y) * bestT
            ),
            halfW = halfW,
            halfH = halfH,
            screenW = screenW,
            screenH = screenH,
            gameRect = gameRect,
            topHudHeightPx = topHudHeightPx,
            isLandscape = isLandscape
        )
        if (isRectValid(bestSegmentPt)) {
            candidates.add(bestSegmentPt)
        }
    }

    if (candidates.isNotEmpty()) {
        val primaryLeft = primary.x < screenW * 0.5f
        val sameSideCandidates = if (isLandscape) {
            candidates.filter { (it.x < screenW * 0.5f) == primaryLeft }
        } else {
            candidates
        }
        val pool = sameSideCandidates.ifEmpty { candidates }
        return pool.minByOrNull { hypot(it.x - primary.x, it.y - primary.y) } ?: clampedFallback
    }

    // 3. Grid search across valid control zones if fallback itself was invalid
    val gridCandidates = ArrayList<Offset>()
    if (isLandscape) {
        val leftXMin = halfW + 4f
        val leftXMax = (gameRect.left - halfW - 4f).coerceAtLeast(leftXMin)
        val rightXMin = (gameRect.right + halfW + 4f).coerceAtMost(screenW - halfW - 4f)
        val rightXMax = (screenW - halfW - 4f).coerceAtLeast(rightXMin)
        val yMin = halfH + 4f
        val yMax = (screenH - halfH - 4f).coerceAtLeast(yMin)

        for (ix in 0..4) {
            val fx = ix / 4f
            val lx = leftXMin + (leftXMax - leftXMin) * fx
            val rx = rightXMin + (rightXMax - rightXMin) * fx
            for (iy in 0..10) {
                val fy = iy / 10f
                val cy = yMin + (yMax - yMin) * fy
                val cLeft = clampControlCenterOutsideGame(
                    Offset(lx, cy), halfW, halfH, screenW, screenH, gameRect, topHudHeightPx, true
                )
                if (isRectValid(cLeft)) gridCandidates.add(cLeft)
                val cRight = clampControlCenterOutsideGame(
                    Offset(rx, cy), halfW, halfH, screenW, screenH, gameRect, topHudHeightPx, true
                )
                if (isRectValid(cRight)) gridCandidates.add(cRight)
            }
        }
    } else {
        val xMin = halfW + 4f
        val xMax = (screenW - halfW - 4f).coerceAtLeast(xMin)
        val yMin = (gameRect.bottom + halfH + 4f).coerceAtMost(screenH - halfH - 4f)
        val yMax = (screenH - halfH - 4f).coerceAtLeast(yMin)
        for (ix in 0..8) {
            val cx = xMin + (xMax - xMin) * (ix / 8f)
            for (iy in 0..8) {
                val cy = yMin + (yMax - yMin) * (iy / 8f)
                val c = clampControlCenterOutsideGame(
                    Offset(cx, cy), halfW, halfH, screenW, screenH, gameRect, topHudHeightPx, false
                )
                if (isRectValid(c)) gridCandidates.add(c)
            }
        }
    }

    if (gridCandidates.isNotEmpty()) {
        return gridCandidates.minByOrNull { hypot(it.x - primary.x, it.y - primary.y) } ?: clampedFallback
    }

    return clampedFallback
}

@Composable
fun VirtualGamepadOverlay(
    layout: ControlLayoutEntity,
    isEditing: Boolean,
    isPaused: Boolean,
    isTurboActive: Boolean,
    audioEnabled: Boolean,
    gameRect: Rect,
    topHudHeightPx: Float,
    onLayoutChange: (ControlLayoutEntity) -> Unit,
    onResetLayout: () -> Unit,
    onSaveLayout: () -> Unit,
    onToggleTurbo: () -> Unit,
    onTogglePause: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onToggleAudio: () -> Unit,
    onInputChanged: (
        up: Boolean,
        down: Boolean,
        left: Boolean,
        right: Boolean,
        a: Boolean,
        b: Boolean,
        start: Boolean,
        select: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier,
    showVirtualControls: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val controlsVisible = showVirtualControls || isEditing

    var selectedGroup by remember { mutableStateOf(EditableControlGroup.DPAD) }
    var editorMinimized by remember { mutableStateOf(false) }
    var editorDockedBottom by remember { mutableStateOf(false) }

    val currentLayout by rememberUpdatedState(layout)
    val currentGameRect by rememberUpdatedState(gameRect)
    val currentTopHudPx by rememberUpdatedState(topHudHeightPx)
    val currentOnLayoutChange by rememberUpdatedState(onLayoutChange)
    val currentOnInputChanged by rememberUpdatedState(onInputChanged)

    // Visual pressed state for immediate UI glow
    var dpadUp by remember { mutableStateOf(false) }
    var dpadDown by remember { mutableStateOf(false) }
    var dpadLeft by remember { mutableStateOf(false) }
    var dpadRight by remember { mutableStateOf(false) }
    var btnA by remember { mutableStateOf(false) }
    var btnB by remember { mutableStateOf(false) }
    var btnStart by remember { mutableStateOf(false) }
    var btnSelect by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            dpadUp = false
            dpadDown = false
            dpadLeft = false
            dpadRight = false
            btnA = false
            btnB = false
            btnStart = false
            btnSelect = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("virtual_gamepad_overlay")
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val isLandscape = widthPx > heightPx

        // Compute maximum allowed scale in Landscape so controls never exceed the side pillar width
        val sidePillarWidthPx = if (isLandscape) {
            minOf(gameRect.left, widthPx - gameRect.right).coerceAtLeast(with(density) { 96.dp.toPx() })
        } else {
            widthPx
        }
        val usablePillarW = (sidePillarWidthPx - 12f).coerceAtLeast(60f)

        val dpadBaseDp = 136.dp
        val maxDpadScale = (usablePillarW / with(density) { dpadBaseDp.toPx() }).coerceIn(0.65f, 1.55f)
        val effDpadScale = layout.dpadScale.coerceIn(0.65f, maxDpadScale)
        val dpadSizeDp = dpadBaseDp * effDpadScale
        val dpadRadiusPx = with(density) { (dpadSizeDp / 2).toPx() }

        val abBaseW = 140.dp
        val abBaseH = 84.dp
        val maxAbScale = (usablePillarW / with(density) { abBaseW.toPx() }).coerceIn(0.65f, 1.55f)
        val effAbScale = layout.abScale.coerceIn(0.65f, maxAbScale)
        val abWidthDp = abBaseW * effAbScale
        val abHeightDp = abBaseH * effAbScale
        val abWpx = with(density) { abWidthDp.toPx() }
        val abHpx = with(density) { abHeightDp.toPx() }

        val ssBaseW = 136.dp
        val ssBaseH = 42.dp
        val maxSsScale = (usablePillarW / with(density) { ssBaseW.toPx() }).coerceIn(0.65f, 1.55f)
        val effSsScale = layout.startSelectScale.coerceIn(0.65f, maxSsScale)
        val ssWidthDp = ssBaseW * effSsScale
        val ssHeightDp = ssBaseH * effSsScale
        val ssWpx = with(density) { ssWidthDp.toPx() }
        val ssHpx = with(density) { ssHeightDp.toPx() }

        // Rectangular Top-Right Action Bar ([>> TURBO] [Pause] [Screenshot] [Save] [Load] [Audio])
        val turboBarBaseW = 216.dp
        val turboBarBaseH = 40.dp
        val maxTurboScale = (usablePillarW / with(density) { turboBarBaseW.toPx() }).coerceIn(0.50f, 1.40f)
        val effTurboScale = layout.turboScale.coerceIn(0.50f, maxTurboScale)
        val turboBarWidthDp = turboBarBaseW * effTurboScale
        val turboBarHeightDp = turboBarBaseH * effTurboScale
        val turboWpx = with(density) { turboBarWidthDp.toPx() }
        val turboHpx = with(density) { turboBarHeightDp.toPx() }

        // Resolve all 4 control centers with strict anti-overlap guarantee
        val placedRects = ArrayList<Rect>(4)

        val dpadDesired = Offset(layout.dpadX * widthPx, layout.dpadY * heightPx)
        val dpadCenter = resolveNonOverlappingControlCenter(
            desiredCenterPx = dpadDesired,
            fallbackCenterPx = dpadDesired,
            halfW = dpadRadiusPx,
            halfH = dpadRadiusPx,
            screenW = widthPx,
            screenH = heightPx,
            gameRect = gameRect,
            topHudHeightPx = topHudHeightPx,
            isLandscape = isLandscape,
            otherControlRects = placedRects
        )
        placedRects.add(
            Rect(
                dpadCenter.x - dpadRadiusPx,
                dpadCenter.y - dpadRadiusPx,
                dpadCenter.x + dpadRadiusPx,
                dpadCenter.y + dpadRadiusPx
            )
        )

        val abDesired = Offset(layout.abX * widthPx, layout.abY * heightPx)
        val abCenter = resolveNonOverlappingControlCenter(
            desiredCenterPx = abDesired,
            fallbackCenterPx = abDesired,
            halfW = abWpx / 2f,
            halfH = abHpx / 2f,
            screenW = widthPx,
            screenH = heightPx,
            gameRect = gameRect,
            topHudHeightPx = topHudHeightPx,
            isLandscape = isLandscape,
            otherControlRects = placedRects
        )
        placedRects.add(
            Rect(
                abCenter.x - abWpx / 2f,
                abCenter.y - abHpx / 2f,
                abCenter.x + abWpx / 2f,
                abCenter.y + abHpx / 2f
            )
        )

        val ssDesired = Offset(layout.startSelectX * widthPx, layout.startSelectY * heightPx)
        val ssCenter = resolveNonOverlappingControlCenter(
            desiredCenterPx = ssDesired,
            fallbackCenterPx = ssDesired,
            halfW = ssWpx / 2f,
            halfH = ssHpx / 2f,
            screenW = widthPx,
            screenH = heightPx,
            gameRect = gameRect,
            topHudHeightPx = topHudHeightPx,
            isLandscape = isLandscape,
            otherControlRects = placedRects
        )
        placedRects.add(
            Rect(
                ssCenter.x - ssWpx / 2f,
                ssCenter.y - ssHpx / 2f,
                ssCenter.x + ssWpx / 2f,
                ssCenter.y + ssHpx / 2f
            )
        )

        val turboDesired = Offset(layout.turboX * widthPx, layout.turboY * heightPx)
        val turboCenter = resolveNonOverlappingControlCenter(
            desiredCenterPx = turboDesired,
            fallbackCenterPx = turboDesired,
            halfW = turboWpx / 2f,
            halfH = turboHpx / 2f,
            screenW = widthPx,
            screenH = heightPx,
            gameRect = gameRect,
            topHudHeightPx = topHudHeightPx,
            isLandscape = isLandscape,
            otherControlRects = placedRects
        )

        val currentCenters by rememberUpdatedState(
            mapOf(
                EditableControlGroup.DPAD to dpadCenter,
                EditableControlGroup.AB_BUTTONS to abCenter,
                EditableControlGroup.START_SELECT to ssCenter,
                EditableControlGroup.TURBO to turboCenter
            )
        )
        val currentHalfSizes by rememberUpdatedState(
            mapOf(
                EditableControlGroup.DPAD to (dpadRadiusPx to dpadRadiusPx),
                EditableControlGroup.AB_BUTTONS to (abWpx / 2f to abHpx / 2f),
                EditableControlGroup.START_SELECT to (ssWpx / 2f to ssHpx / 2f),
                EditableControlGroup.TURBO to (turboWpx / 2f to turboHpx / 2f)
            )
        )

        val touchInteractionModifier = if (controlsVisible) {
            Modifier.pointerInput(isEditing, controlsVisible, widthPx, heightPx) {
                if (isEditing) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchPt = down.position
                        val centers = currentCenters
                        val halfSizes = currentHalfSizes

                        val curDpadCenter = centers.getValue(EditableControlGroup.DPAD)
                        val curDpadR = halfSizes.getValue(EditableControlGroup.DPAD).first
                        val curAbCenter = centers.getValue(EditableControlGroup.AB_BUTTONS)
                        val (curAbHalfW, curAbHalfH) = halfSizes.getValue(EditableControlGroup.AB_BUTTONS)
                        val curSsCenter = centers.getValue(EditableControlGroup.START_SELECT)
                        val (curSsHalfW, curSsHalfH) = halfSizes.getValue(EditableControlGroup.START_SELECT)
                        val curTurboCenter = centers.getValue(EditableControlGroup.TURBO)
                        val (curTurboHalfW, curTurboHalfH) = halfSizes.getValue(EditableControlGroup.TURBO)

                        val hitGroup: EditableControlGroup? = when {
                            abs(touchPt.x - curTurboCenter.x) <= curTurboHalfW * 1.15f &&
                                abs(touchPt.y - curTurboCenter.y) <= curTurboHalfH * 1.35f ->
                                EditableControlGroup.TURBO
                            abs(touchPt.x - curSsCenter.x) <= curSsHalfW * 1.2f &&
                                abs(touchPt.y - curSsCenter.y) <= curSsHalfH * 1.4f ->
                                EditableControlGroup.START_SELECT
                            abs(touchPt.x - curAbCenter.x) <= curAbHalfW * 1.15f &&
                                abs(touchPt.y - curAbCenter.y) <= curAbHalfH * 1.25f ->
                                EditableControlGroup.AB_BUTTONS
                            hypot(touchPt.x - curDpadCenter.x, touchPt.y - curDpadCenter.y) <= curDpadR * 1.25f ->
                                EditableControlGroup.DPAD
                            else -> null
                        }

                        if (hitGroup != null) {
                            selectedGroup = hitGroup
                            down.consume()

                            var lastValidCenter = centers.getValue(hitGroup)
                            val grabOffset = touchPt - lastValidCenter

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val dragChange = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (dragChange != null && dragChange.pressed) {
                                    dragChange.consume()
                                    val rawTarget = dragChange.position - grabOffset
                                    val latest = currentLayout
                                    val latestCenters = currentCenters
                                    val latestHalfSizes = currentHalfSizes
                                    val (halfW, halfH) = latestHalfSizes.getValue(hitGroup)

                                    val obstacleRects = EditableControlGroup.entries
                                        .filter { it != hitGroup }
                                        .map { other ->
                                            val c = latestCenters.getValue(other)
                                            val (hw, hh) = latestHalfSizes.getValue(other)
                                            Rect(c.x - hw, c.y - hh, c.x + hw, c.y + hh)
                                        }

                                    val resolvedCenterPx = resolveNonOverlappingControlCenter(
                                        desiredCenterPx = rawTarget,
                                        fallbackCenterPx = lastValidCenter,
                                        halfW = halfW,
                                        halfH = halfH,
                                        screenW = widthPx,
                                        screenH = heightPx,
                                        gameRect = currentGameRect,
                                        topHudHeightPx = currentTopHudPx,
                                        isLandscape = isLandscape,
                                        otherControlRects = obstacleRects
                                    )
                                    lastValidCenter = resolvedCenterPx

                                    val nx = (resolvedCenterPx.x / widthPx).coerceIn(0f, 1f)
                                    val ny = (resolvedCenterPx.y / heightPx).coerceIn(0f, 1f)

                                    val updated = when (hitGroup) {
                                        EditableControlGroup.DPAD -> latest.copy(dpadX = nx, dpadY = ny)
                                        EditableControlGroup.AB_BUTTONS -> latest.copy(abX = nx, abY = ny)
                                        EditableControlGroup.START_SELECT -> latest.copy(startSelectX = nx, startSelectY = ny)
                                        EditableControlGroup.TURBO -> latest.copy(turboX = nx, turboY = ny)
                                    }
                                    currentOnLayoutChange(updated)
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val activePointers = event.changes.filter { it.pressed }.map { it.position }
                            val snap = currentLayout
                            val centers = currentCenters
                            val halfSizes = currentHalfSizes

                            val curDpadCenter = centers.getValue(EditableControlGroup.DPAD)
                            val curDpadRadiusPx = halfSizes.getValue(EditableControlGroup.DPAD).first

                            val curAbCenter = centers.getValue(EditableControlGroup.AB_BUTTONS)
                            val sAb = (halfSizes.getValue(EditableControlGroup.AB_BUTTONS).first * 2f) /
                                with(density) { abBaseW.toPx() }
                            val curBtnRadiusPx = with(density) { (31.dp * sAb).toPx() }
                            val curBCenter = Offset(
                                curAbCenter.x - with(density) { (33.dp * sAb).toPx() },
                                curAbCenter.y + with(density) { (12.dp * sAb).toPx() }
                            )
                            val curACenter = Offset(
                                curAbCenter.x + with(density) { (33.dp * sAb).toPx() },
                                curAbCenter.y - with(density) { (12.dp * sAb).toPx() }
                            )

                            val curSsCenter = centers.getValue(EditableControlGroup.START_SELECT)
                            val sSs = (halfSizes.getValue(EditableControlGroup.START_SELECT).first * 2f) /
                                with(density) { ssBaseW.toPx() }
                            val curSelectCenter = Offset(
                                curSsCenter.x - with(density) { (32.dp * sSs).toPx() },
                                curSsCenter.y
                            )
                            val curStartCenter = Offset(
                                curSsCenter.x + with(density) { (32.dp * sSs).toPx() },
                                curSsCenter.y
                            )
                            val curSsHitRadiusPx = with(density) { (28.dp * sSs).toPx() }

                            var newUp = false
                            var newDown = false
                            var newLeft = false
                            var newRight = false
                            var newA = false
                            var newB = false
                            var newStart = false
                            var newSelect = false

                            for (pt in activePointers) {
                                val dx = pt.x - curDpadCenter.x
                                val dy = pt.y - curDpadCenter.y
                                val distDpad = hypot(dx, dy)
                                if (distDpad <= curDpadRadiusPx * 1.25f) {
                                    val deadzonePx = curDpadRadiusPx * snap.dpadDeadzone
                                    if (distDpad > deadzonePx) {
                                        val normX = dx / distDpad
                                        val normY = dy / distDpad
                                        if (normX < -0.38f) newLeft = true
                                        if (normX > 0.38f) newRight = true
                                        if (normY < -0.38f) newUp = true
                                        if (normY > 0.38f) newDown = true
                                    }
                                }

                                if (hypot(pt.x - curBCenter.x, pt.y - curBCenter.y) <= curBtnRadiusPx * 1.22f) newB = true
                                if (hypot(pt.x - curACenter.x, pt.y - curACenter.y) <= curBtnRadiusPx * 1.22f) newA = true

                                if (hypot(pt.x - curSelectCenter.x, pt.y - curSelectCenter.y) <= curSsHitRadiusPx) {
                                    newSelect = true
                                }
                                if (hypot(pt.x - curStartCenter.x, pt.y - curStartCenter.y) <= curSsHitRadiusPx) {
                                    newStart = true
                                }
                            }

                            val newlyPressed = (newUp && !dpadUp) ||
                                (newDown && !dpadDown) ||
                                (newLeft && !dpadLeft) ||
                                (newRight && !dpadRight) ||
                                (newA && !btnA) ||
                                (newB && !btnB) ||
                                (newStart && !btnStart) ||
                                (newSelect && !btnSelect)

                            if (newlyPressed && snap.hapticEnabled) {
                                triggerHapticPulse(context, snap.hapticIntensity)
                            }

                            dpadUp = newUp
                            dpadDown = newDown
                            dpadLeft = newLeft
                            dpadRight = newRight
                            btnA = newA
                            btnB = newB
                            btnStart = newStart
                            btnSelect = newSelect

                            currentOnInputChanged(
                                newUp,
                                newDown,
                                newLeft,
                                newRight,
                                newA,
                                newB,
                                newStart,
                                newSelect
                            )
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(touchInteractionModifier)
        ) {
            if (controlsVisible) {
                // 1. D-Pad Visual (with independent dpadOpacity)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dpadCenter.x - dpadRadiusPx).roundToInt(),
                                (dpadCenter.y - dpadRadiusPx).roundToInt()
                            )
                        }
                        .size(dpadSizeDp)
                        .alpha(layout.dpadOpacity.coerceIn(0.20f, 1.0f))
                        .then(
                            if (isEditing) {
                                Modifier.border(
                                    width = if (selectedGroup == EditableControlGroup.DPAD) 2.5.dp else 1.5.dp,
                                    color = if (selectedGroup == EditableControlGroup.DPAD) NeonCyan else AtomicPurple,
                                    shape = RoundedCornerShape(16.dp)
                                )
                            } else Modifier
                        )
                        .testTag("virtual_dpad")
                ) {
                    DpadCanvas(
                        upPressed = dpadUp,
                        downPressed = dpadDown,
                        leftPressed = dpadLeft,
                        rightPressed = dpadRight
                    )
                }

                // 2. B / A Buttons Cluster (with independent abOpacity)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (abCenter.x - abWpx / 2f).roundToInt(),
                                (abCenter.y - abHpx / 2f).roundToInt()
                            )
                        }
                        .size(abWidthDp, abHeightDp)
                        .alpha(layout.abOpacity.coerceIn(0.20f, 1.0f))
                        .then(
                            if (isEditing) {
                                Modifier.border(
                                    width = if (selectedGroup == EditableControlGroup.AB_BUTTONS) 2.5.dp else 1.5.dp,
                                    color = if (selectedGroup == EditableControlGroup.AB_BUTTONS) NeonCyan else AtomicPurple,
                                    shape = RoundedCornerShape(16.dp)
                                )
                            } else Modifier
                        )
                        .testTag("virtual_ab_cluster")
                ) {
                    AbButtonsCanvas(
                        aPressed = btnA,
                        bPressed = btnB,
                        scale = effAbScale
                    )
                }

                // 3. Select / Start Cluster (with independent startSelectOpacity)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (ssCenter.x - ssWpx / 2f).roundToInt(),
                                (ssCenter.y - ssHpx / 2f).roundToInt()
                            )
                        }
                        .size(ssWidthDp, ssHeightDp)
                        .alpha(layout.startSelectOpacity.coerceIn(0.20f, 1.0f))
                        .then(
                            if (isEditing) {
                                Modifier.border(
                                    width = if (selectedGroup == EditableControlGroup.START_SELECT) 2.5.dp else 1.5.dp,
                                    color = if (selectedGroup == EditableControlGroup.START_SELECT) NeonCyan else AtomicPurple,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            } else Modifier
                        )
                        .testTag("virtual_start_select")
                ) {
                    StartSelectCanvas(
                        selectPressed = btnSelect,
                        startPressed = btnStart,
                        scale = effSsScale
                    )
                }
            }

            // 4. Rectangular Turbo & Quick Action Bar ([>> TURBO] [Pause] [Screenshot] [Save] [Load] [Audio])
            // Exclusively during `isEditing == true`, click handlers are disabled so tapping/dragging
            // the top-right buttons edits them without EVER accidentally triggering their actions!
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (turboCenter.x - turboWpx / 2f).roundToInt(),
                            (turboCenter.y - turboHpx / 2f).roundToInt()
                        )
                    }
                    .size(turboBarWidthDp, turboBarHeightDp)
                    .alpha(layout.turboOpacity.coerceIn(0.20f, 1.0f))
                    .then(
                        if (isEditing) {
                            Modifier.border(
                                width = if (selectedGroup == EditableControlGroup.TURBO) 2.5.dp else 1.5.dp,
                                color = if (selectedGroup == EditableControlGroup.TURBO) NeonCyan else AtomicPurple,
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else Modifier
                    )
                    .testTag("virtual_turbo_bar"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp * effTurboScale),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rectangular TURBO Button
                    Box(
                        modifier = Modifier
                            .width(52.dp * effTurboScale)
                            .height(32.dp * effTurboScale)
                            .clip(RoundedCornerShape(8.dp * effTurboScale))
                            .background(
                                if (isTurboActive) CyberYellow else OledSurfaceVariant.copy(alpha = 0.95f)
                            )
                            .border(
                                1.dp,
                                CyberYellow.copy(alpha = 0.75f),
                                RoundedCornerShape(8.dp * effTurboScale)
                            )
                            .then(
                                if (!isEditing) {
                                    Modifier.clickable { onToggleTurbo() }
                                } else Modifier
                            )
                            .testTag("speed_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = NeoIcons.FastForward,
                                contentDescription = "Modo Turbo",
                                tint = if (isTurboActive) OledBlack else CyberYellow,
                                modifier = Modifier.size(11.dp * effTurboScale)
                            )
                            Spacer(modifier = Modifier.width(1.5.dp * effTurboScale))
                            Text(
                                text = "TURBO",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTurboActive) OledBlack else CyberYellow,
                                fontSize = (8.5f * effTurboScale).coerceIn(6.5f, 11f).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // Pause / Resume Emulator Button
                    Box(
                        modifier = Modifier
                            .width(30.dp * effTurboScale)
                            .height(32.dp * effTurboScale)
                            .clip(RoundedCornerShape(8.dp * effTurboScale))
                            .background(
                                if (isPaused) CyberYellow else OledSurfaceVariant.copy(alpha = 0.95f)
                            )
                            .border(
                                1.dp,
                                (if (isPaused) CyberYellow else NeonCyan).copy(alpha = 0.65f),
                                RoundedCornerShape(8.dp * effTurboScale)
                            )
                            .then(
                                if (!isEditing) {
                                    Modifier.clickable { onTogglePause() }
                                } else Modifier
                            )
                            .testTag("pause_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPaused) NeoIcons.PlayArrow else NeoIcons.Pause,
                            contentDescription = if (isPaused) "Retomar Emulador" else "Pausar Emulador",
                            tint = if (isPaused) OledBlack else NeonCyan,
                            modifier = Modifier.size(15.dp * effTurboScale)
                        )
                    }

                    // Game-Only Screenshot to Gallery Button
                    Box(
                        modifier = Modifier
                            .width(30.dp * effTurboScale)
                            .height(32.dp * effTurboScale)
                            .clip(RoundedCornerShape(8.dp * effTurboScale))
                            .background(OledSurfaceVariant.copy(alpha = 0.95f))
                            .border(
                                1.dp,
                                AtomicPurple.copy(alpha = 0.65f),
                                RoundedCornerShape(8.dp * effTurboScale)
                            )
                            .then(
                                if (!isEditing) {
                                    Modifier.clickable { onTakeScreenshot() }
                                } else Modifier
                            )
                            .testTag("screenshot_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NeoIcons.CameraAlt,
                            contentDescription = "Capturar Tela na Galeria",
                            tint = AtomicPurple,
                            modifier = Modifier.size(15.dp * effTurboScale)
                        )
                    }

                    // Quick Save Button
                    Box(
                        modifier = Modifier
                            .width(30.dp * effTurboScale)
                            .height(32.dp * effTurboScale)
                            .clip(RoundedCornerShape(8.dp * effTurboScale))
                            .background(OledSurfaceVariant.copy(alpha = 0.95f))
                            .border(
                                1.dp,
                                EmeraldSuccess.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp * effTurboScale)
                            )
                            .then(
                                if (!isEditing) {
                                    Modifier.clickable { onQuickSave() }
                                } else Modifier
                            )
                            .testTag("quick_save_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NeoIcons.BookmarkAdd,
                            contentDescription = "Salvar Rápido",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(15.dp * effTurboScale)
                        )
                    }

                    // Quick Load Button
                    Box(
                        modifier = Modifier
                            .width(30.dp * effTurboScale)
                            .height(32.dp * effTurboScale)
                            .clip(RoundedCornerShape(8.dp * effTurboScale))
                            .background(OledSurfaceVariant.copy(alpha = 0.95f))
                            .border(
                                1.dp,
                                MatrixBlue.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp * effTurboScale)
                            )
                            .then(
                                if (!isEditing) {
                                    Modifier.clickable { onQuickLoad() }
                                } else Modifier
                            )
                            .testTag("quick_load_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NeoIcons.History,
                            contentDescription = "Carregar Rápido",
                            tint = MatrixBlue,
                            modifier = Modifier.size(15.dp * effTurboScale)
                        )
                    }

                    // Audio Toggle Button
                    Box(
                        modifier = Modifier
                            .width(30.dp * effTurboScale)
                            .height(32.dp * effTurboScale)
                            .clip(RoundedCornerShape(8.dp * effTurboScale))
                            .background(OledSurfaceVariant.copy(alpha = 0.95f))
                            .border(
                                1.dp,
                                (if (audioEnabled) NeonCyan else BerryMagenta).copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp * effTurboScale)
                            )
                            .then(
                                if (!isEditing) {
                                    Modifier.clickable { onToggleAudio() }
                                } else Modifier
                            )
                            .testTag("audio_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (audioEnabled) NeoIcons.VolumeUp else NeoIcons.VolumeOff,
                            contentDescription = "Alternar Áudio",
                            tint = if (audioEnabled) NeonCyan else BerryMagenta,
                            modifier = Modifier.size(15.dp * effTurboScale)
                        )
                    }
                }
            }

            // 5. Compact, Minimizable & Repositionable Floating Control Editor Panel
            if (isEditing) {
                val maxScaleForSelected = when (selectedGroup) {
                    EditableControlGroup.DPAD -> maxDpadScale
                    EditableControlGroup.AB_BUTTONS -> maxAbScale
                    EditableControlGroup.START_SELECT -> maxSsScale
                    EditableControlGroup.TURBO -> maxTurboScale
                }

                TouchLayoutEditorPanel(
                    layout = layout,
                    isLandscape = isLandscape,
                    isMinimized = editorMinimized,
                    isDockedBottom = editorDockedBottom,
                    selectedGroup = selectedGroup,
                    maxAllowedScale = maxScaleForSelected,
                    onSelectGroup = { selectedGroup = it },
                    onToggleMinimize = { editorMinimized = !editorMinimized },
                    onToggleDockPosition = { editorDockedBottom = !editorDockedBottom },
                    onLayoutChange = onLayoutChange,
                    onReset = onResetLayout,
                    onSave = onSaveLayout,
                    modifier = Modifier
                        .align(if (editorDockedBottom) Alignment.BottomCenter else Alignment.TopCenter)
                        .padding(
                            top = if (editorDockedBottom) 0.dp else 8.dp,
                            bottom = if (editorDockedBottom) 8.dp else 0.dp,
                            start = 12.dp,
                            end = 12.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun TouchLayoutEditorPanel(
    layout: ControlLayoutEntity,
    isLandscape: Boolean,
    isMinimized: Boolean,
    isDockedBottom: Boolean,
    selectedGroup: EditableControlGroup,
    maxAllowedScale: Float,
    onSelectGroup: (EditableControlGroup) -> Unit,
    onToggleMinimize: () -> Unit,
    onToggleDockPosition: () -> Unit,
    onLayoutChange: (ControlLayoutEntity) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minScale = if (selectedGroup == EditableControlGroup.TURBO) 0.50f else 0.65f
    Column(
        modifier = modifier
            .widthIn(max = if (isLandscape) 370.dp else 440.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OledSurface.copy(alpha = 0.94f))
            .border(1.5.dp, NeonCyan.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("touch_layout_editor_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Editor (${selectedGroup.label})",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Ações bloqueadas na edição • Antissobreposição ativa",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleDockPosition,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OledSurfaceVariant)
                ) {
                    Icon(
                        imageVector = NeoIcons.SwapVert,
                        contentDescription = if (isDockedBottom) "Mover painel para o topo" else "Mover painel para baixo",
                        tint = CyberYellow,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleMinimize,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OledSurfaceVariant)
                ) {
                    Icon(
                        imageVector = if (isMinimized) NeoIcons.ExpandMore else NeoIcons.ExpandLess,
                        contentDescription = if (isMinimized) "Expandir ajustes" else "Minimizar painel",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = OledBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("save_controls_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Salvar Layout",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Salvar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (!isMinimized) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                EditableControlGroup.entries.forEach { group ->
                    val selected = group == selectedGroup
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectGroup(group) },
                        label = {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                            selectedLabelColor = NeonCyan,
                            containerColor = OledBlack
                        ),
                        modifier = Modifier.testTag("editor_chip_${group.name.lowercase()}")
                    )
                }
            }

            val rawScale = when (selectedGroup) {
                EditableControlGroup.DPAD -> layout.dpadScale
                EditableControlGroup.AB_BUTTONS -> layout.abScale
                EditableControlGroup.START_SELECT -> layout.startSelectScale
                EditableControlGroup.TURBO -> layout.turboScale
            }
            val currentScale = rawScale.coerceIn(minScale, maxAllowedScale)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Escala (${(currentScale * 100).roundToInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    modifier = Modifier.width(92.dp)
                )
                Slider(
                    value = currentScale,
                    onValueChange = { s ->
                        val clampedS = s.coerceIn(minScale, maxAllowedScale)
                        val updated = when (selectedGroup) {
                            EditableControlGroup.DPAD -> layout.copy(dpadScale = clampedS)
                            EditableControlGroup.AB_BUTTONS -> layout.copy(abScale = clampedS)
                            EditableControlGroup.START_SELECT -> layout.copy(startSelectScale = clampedS)
                            EditableControlGroup.TURBO -> layout.copy(turboScale = clampedS)
                        }
                        onLayoutChange(updated)
                    },
                    valueRange = minScale..maxAllowedScale.coerceAtLeast(minScale + 0.05f),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("editor_scale_slider")
                )
            }

            val currentGroupOpacity = when (selectedGroup) {
                EditableControlGroup.DPAD -> layout.dpadOpacity
                EditableControlGroup.AB_BUTTONS -> layout.abOpacity
                EditableControlGroup.START_SELECT -> layout.startSelectOpacity
                EditableControlGroup.TURBO -> layout.turboOpacity
            }.coerceIn(0.20f, 1.0f)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Opacidade (${(currentGroupOpacity * 100).roundToInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    modifier = Modifier.width(92.dp)
                )
                Slider(
                    value = currentGroupOpacity,
                    onValueChange = { op ->
                        val clampedOp = op.coerceIn(0.20f, 1.0f)
                        val updated = when (selectedGroup) {
                            EditableControlGroup.DPAD -> layout.copy(dpadOpacity = clampedOp)
                            EditableControlGroup.AB_BUTTONS -> layout.copy(abOpacity = clampedOp)
                            EditableControlGroup.START_SELECT -> layout.copy(startSelectOpacity = clampedOp)
                            EditableControlGroup.TURBO -> layout.copy(turboOpacity = clampedOp)
                        }
                        onLayoutChange(updated)
                    },
                    valueRange = 0.20f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = AtomicPurple,
                        activeTrackColor = AtomicPurple
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("editor_opacity_slider")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onLayoutChange(layout.copy(hapticEnabled = !layout.hapticEnabled))
                    }
                ) {
                    Text(
                        text = "Vibração Tátil",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = layout.hapticEnabled,
                        onCheckedChange = { onLayoutChange(layout.copy(hapticEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OledBlack,
                            checkedTrackColor = NeonCyan
                        )
                    )
                }

                OutlinedButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("reset_controls_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restaurar",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Padrão", color = TextPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DpadCanvas(
    upPressed: Boolean,
    downPressed: Boolean,
    leftPressed: Boolean,
    rightPressed: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val armThickness = w * 0.34f
        val armOffset = (w - armThickness) / 2f

        drawCircle(
            color = Color(0xFF131724).copy(alpha = 0.85f),
            radius = w * 0.49f,
            center = Offset(w / 2f, h / 2f)
        )
        drawCircle(
            color = OledCardBorder,
            radius = w * 0.49f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = 2.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFF1E2438),
            topLeft = Offset(armOffset, w * 0.06f),
            size = Size(armThickness, h * 0.88f),
            cornerRadius = CornerRadius(14f, 14f)
        )

        drawRoundRect(
            color = Color(0xFF1E2438),
            topLeft = Offset(w * 0.06f, armOffset),
            size = Size(w * 0.88f, armThickness),
            cornerRadius = CornerRadius(14f, 14f)
        )

        if (upPressed) {
            drawRoundRect(
                color = NeonCyan.copy(alpha = 0.7f),
                topLeft = Offset(armOffset + 4f, w * 0.07f),
                size = Size(armThickness - 8f, armOffset - w * 0.05f),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }
        if (downPressed) {
            drawRoundRect(
                color = NeonCyan.copy(alpha = 0.7f),
                topLeft = Offset(armOffset + 4f, armOffset + armThickness),
                size = Size(armThickness - 8f, armOffset - w * 0.05f),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }
        if (leftPressed) {
            drawRoundRect(
                color = NeonCyan.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.07f, armOffset + 4f),
                size = Size(armOffset - w * 0.05f, armThickness - 8f),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }
        if (rightPressed) {
            drawRoundRect(
                color = NeonCyan.copy(alpha = 0.7f),
                topLeft = Offset(armOffset + armThickness, armOffset + 4f),
                size = Size(armOffset - w * 0.05f, armThickness - 8f),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }

        val arrowColor = NeonCyan
        val upPath = Path().apply {
            moveTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.42f, h * 0.25f)
            lineTo(w * 0.58f, h * 0.25f)
            close()
        }
        drawPath(upPath, if (upPressed) OledBlack else arrowColor)

        val downPath = Path().apply {
            moveTo(w * 0.5f, h * 0.86f)
            lineTo(w * 0.42f, h * 0.75f)
            lineTo(w * 0.58f, h * 0.75f)
            close()
        }
        drawPath(downPath, if (downPressed) OledBlack else arrowColor)

        val leftPath = Path().apply {
            moveTo(w * 0.14f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.42f)
            lineTo(w * 0.25f, h * 0.58f)
            close()
        }
        drawPath(leftPath, if (leftPressed) OledBlack else arrowColor)

        val rightPath = Path().apply {
            moveTo(w * 0.86f, h * 0.5f)
            lineTo(w * 0.75f, h * 0.42f)
            lineTo(w * 0.75f, h * 0.58f)
            close()
        }
        drawPath(rightPath, if (rightPressed) OledBlack else arrowColor)

        drawCircle(
            color = Color(0xFF121624),
            radius = armThickness * 0.26f,
            center = Offset(w / 2f, h / 2f)
        )
    }
}

@Composable
private fun AbButtonsCanvas(
    aPressed: Boolean,
    bPressed: Boolean,
    scale: Float
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp)
                .size(58.dp * scale)
                .clip(CircleShape)
                .background(if (bPressed) BerryMagenta else Color(0xFF231734))
                .border(2.5.dp, BerryMagenta, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.headlineMedium,
                color = if (bPressed) OledBlack else BerryMagenta,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 4.dp)
                .size(58.dp * scale)
                .clip(CircleShape)
                .background(if (aPressed) NeonCyan else Color(0xFF102A2E))
                .border(2.5.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.headlineMedium,
                color = if (aPressed) OledBlack else NeonCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StartSelectCanvas(
    selectPressed: Boolean,
    startPressed: Boolean,
    scale: Float
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(58.dp * scale)
                .height(26.dp * scale)
                .clip(RoundedCornerShape(13.dp))
                .background(if (selectPressed) AtomicPurple else Color(0xFF1B2032))
                .border(1.5.dp, AtomicPurple, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SELECT",
                style = MaterialTheme.typography.labelSmall,
                color = if (selectPressed) OledBlack else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = (8.5f * scale).coerceIn(7f, 11f).sp
            )
        }

        Box(
            modifier = Modifier
                .width(58.dp * scale)
                .height(26.dp * scale)
                .clip(RoundedCornerShape(13.dp))
                .background(if (startPressed) NeonCyan else Color(0xFF1B2032))
                .border(1.5.dp, NeonCyan, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "START",
                style = MaterialTheme.typography.labelSmall,
                color = if (startPressed) OledBlack else NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = (8.5f * scale).coerceIn(7f, 11f).sp
            )
        }
    }
}

private fun triggerHapticPulse(context: Context, intensity: Float) {
    try {
        val durationMs = (14L * intensity.coerceIn(0.3f, 1.5f)).toLong().coerceAtLeast(6L)
        val amplitude = (160 * intensity.coerceIn(0.3f, 1.5f)).toInt().coerceIn(40, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {
    }
}
