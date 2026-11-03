package com.example.data

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.AtomicPurple
import com.example.ui.theme.BerryMagenta
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.MatrixBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import java.io.File
import kotlin.math.abs

object CoverArtProvider {

    private val KNOWN_HEADER_TO_LIBRETRO = mapOf(
        "POKEMON_GLD" to "Pokemon - Gold Version (USA, Europe)",
        "POKEMON_SLV" to "Pokemon - Silver Version (USA, Europe)",
        "PM_CRYSTAL" to "Pokemon - Crystal Version (USA, Europe)",
        "POKEMON RED" to "Pokemon - Red Version (USA, Europe)",
        "POKEMON BLUE" to "Pokemon - Blue Version (USA, Europe)",
        "POKEMON YELLOW" to "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe)",
        "ZELDA" to "Legend of Zelda, The - Link's Awakening DX (USA, Europe) (Rev 2)",
        "ZELDA DIN" to "Legend of Zelda, The - Oracle of Seasons (USA)",
        "ZELDA NAYRU" to "Legend of Zelda, The - Oracle of Ages (USA)",
        "SUPER MARIOLAND" to "Super Mario Land (World)",
        "MARIOLAND2" to "Super Mario Land 2 - 6 Golden Coins (USA, Europe)",
        "SUPER MARIO DX" to "Super Mario Bros. Deluxe (USA, Europe)",
        "WARIOLAND3" to "Wario Land 3 (World)",
        "WARIOLAND2" to "Wario Land II (USA, Europe)",
        "TETRIS" to "Tetris DX (World)",
        "METROID2" to "Metroid II - Return of Samus (World)",
        "METAL GEAR" to "Metal Gear Solid (USA)",
        "SHANTAE" to "Shantae (USA)",
        "DONKEYKONGLAND95" to "Donkey Kong Country (USA, Europe)"
    )

    fun buildAutomaticCoverUrl(fileName: String, headerTitle: String, isCgb: Boolean): String {
        val cleanHeader = headerTitle.uppercase().trim()
        val matchedKnown = KNOWN_HEADER_TO_LIBRETRO.entries.firstOrNull {
            cleanHeader.startsWith(it.key)
        }?.value

        val candidateName = matchedKnown ?: cleanFileNameForLibretro(fileName, headerTitle)
        val encoded = Uri.encode(candidateName)
        val repo = if (isCgb) {
            "Nintendo_-_Game_Boy_Color"
        } else {
            "Nintendo_-_Game_Boy"
        }
        return "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/Named_Boxarts/$encoded.png"
    }

    fun cleanFileNameForLibretro(fileName: String, fallbackHeader: String): String {
        val withoutExt = fileName
            .removeSuffix(".zip")
            .removeSuffix(".gbc")
            .removeSuffix(".gb")
            .removeSuffix(".cgb")
            .replace("_", " ")
            .trim()

        return withoutExt.ifEmpty { fallbackHeader }
    }

    fun formatDisplayTitle(fileName: String, headerTitle: String): String {
        val cleaned = cleanFileNameForLibretro(fileName, headerTitle)
        return if (cleaned.length >= 3) cleaned else headerTitle
    }
}

@Composable
fun RomCoverArtView(
    rom: RomEntity,
    modifier: Modifier = Modifier
) {
    val activeImageModel: Any? = when {
        !rom.customCoverUri.isNullOrBlank() -> {
            val path = rom.customCoverUri
            if (path.startsWith("/")) File(path) else Uri.parse(path)
        }
        !rom.autoCoverUrl.isNullOrBlank() -> rom.autoCoverUrl
        else -> null
    }

    if (activeImageModel != null) {
        SubcomposeAsyncImage(
            model = activeImageModel,
            contentDescription = "Capa do jogo ${rom.title}",
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = {
                ProceduralCartridgeCover(rom = rom, modifier = Modifier.fillMaxSize())
            },
            error = {
                ProceduralCartridgeCover(rom = rom, modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        ProceduralCartridgeCover(rom = rom, modifier = modifier)
    }
}

@Composable
fun ProceduralCartridgeCover(
    rom: RomEntity,
    modifier: Modifier = Modifier
) {
    val hash = abs(rom.crc32Hex.hashCode())
    val accentColors = listOf(
        NeonCyan to AtomicPurple,
        BerryMagenta to AtomicPurple,
        MatrixBlue to NeonCyan,
        CyberYellow to BerryMagenta
    )
    val (primaryAccent, secondaryAccent) = accentColors[hash % accentColors.size]
    val useCoreEmblem = (hash % 2 == 0)

    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF141826),
                        Color(0xFF0A0D16)
                    )
                )
            )
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Cartridge shell grooves at top
            for (i in 0..3) {
                val y = 6.dp.toPx() + i * 5.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(10.dp.toPx(), y),
                    size = Size(w - 20.dp.toPx(), 2.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }

            // Neon glow circle / geometric artwork inside cartridge label
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = 0.35f),
                        secondaryAccent.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.45f),
                    radius = w * 0.55f
                ),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = w * 0.55f
            )

            if (useCoreEmblem) {
                // Draw stylized cyber-retro core emblem for custom/homebrew cartridges
                val cx = w * 0.5f
                val cy = h * 0.42f
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.85f),
                    radius = w * 0.22f,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = BerryMagenta.copy(alpha = 0.9f),
                    radius = w * 0.11f,
                    center = Offset(cx, cy)
                )
                drawRoundRect(
                    color = NeonCyan.copy(alpha = 0.5f),
                    topLeft = Offset(cx - w * 0.28f, cy - 3.dp.toPx()),
                    size = Size(w * 0.56f, 6.dp.toPx()),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            } else {
                // Retro grid lines
                val step = w / 6f
                for (i in 1..5) {
                    drawLine(
                        color = primaryAccent.copy(alpha = 0.14f),
                        start = Offset(i * step, h * 0.2f),
                        end = Offset(i * step, h * 0.72f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (rom.isCgb) "GAME BOY COLOR" else "GAME BOY",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = rom.cartridgeType.take(4),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary.copy(alpha = 0.5f),
                    fontSize = 8.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(OledBlack.copy(alpha = 0.78f))
                    .padding(8.dp)
            ) {
                Text(
                    text = rom.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CRC ${rom.crc32Hex.take(6)} • ${(rom.romSizeBytes / 1024)}KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryAccent,
                    fontSize = 9.sp
                )
            }
        }
    }
}
