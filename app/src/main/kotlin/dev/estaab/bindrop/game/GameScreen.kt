package dev.estaab.bindrop.game

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.estaab.bindrop.Settings
import dev.estaab.bindrop.audio.Sounds
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val MAX_DT = 0.05f
private const val PARENT_HOLD_MS = 1500L
private const val EMOJI_SAMPLE_SIZE = 2          // 512 px source → 256 px in memory

private val SKY_TOP = Color(0xFF8ED6FF)
private val SKY_BOTTOM = Color(0xFFDDF4FF)
private val SUN = Color(0xFFFFE066)
private val SUN_GLOW = Color(0x55FFE066)
private val CLOUD = Color(0xFFFFFFFF)
private val GRASS = Color(0xFF7BCB5B)
private val GRASS_DARK = Color(0xFF5FAE43)
private val DIRT = Color(0xFFA8794E)
private val SHADOW = Color(0x33000000)
private val INTERIOR = Color(0xFF1B1E24)
private val WHEEL = Color(0xFF2B2B2B)
private val HUB = Color(0xFF9A9A9A)
private val EYE_WHITE = Color(0xFFFFFFFF)
private val PUPIL = Color(0xFF1B1E24)
private val MOUTH = Color(0xFF1B1E24)
private val TONGUE = Color(0xFFE8637A)

private const val CLOUD_COUNT = 3
private const val CLOUD_SPEED_FRAC = 0.02f       // W/s
private const val SUN_X_FRAC = 0.09f
private const val SUN_Y_FRAC = 0.16f
private const val SUN_R_FRAC = 0.07f
private const val GRASS_H_FRAC = 0.035f
private const val BIN_TAPER_FRAC = 0.07f         // of bin width, per side, at the bottom
private const val BIN_LIP_FRAC = 0.045f          // of bin height
private const val LID_H_FRAC = 0.26f             // of bin height
private const val LID_TOP_W_FRAC = 0.88f         // of bin width: the open lid leans back, so its far edge looks narrower
private const val LID_OVERHANG_FRAC = 0.04f      // of bin width, per side
private const val WHEEL_R_FRAC = 0.085f          // of bin height
private const val WHEEL_X_FRAC = 0.08f           // of bin width, in from each side
private const val EYE_Y_FRAC = 0.22f
private const val EYE_DX_FRAC = 0.18f
private const val EYE_RX_FRAC = 0.085f           // of bin width
private const val EYE_RY_FRAC = 0.11f
private const val PUPIL_R_FRAC = 0.045f
private const val PUPIL_TRAVEL_FRAC = 0.035f
private const val MOUTH_Y_FRAC = 0.37f
private const val LABEL_Y_FRAC = 0.48f
private const val LABEL_W_FRAC = 0.62f
private const val LABEL_H_FRAC = 0.2f
private const val SQUASH_AMOUNT = 0.16f
private const val WIGGLE_DEG = 9f
private const val LID_FLAP_AMOUNT = 0.45f

/** Decoded sprites, keyed by drawable id. */
class SpriteCache(resources: Resources) {
    private val map = HashMap<Int, ImageBitmap>()

    init {
        val opts = BitmapFactory.Options().apply { inSampleSize = EMOJI_SAMPLE_SIZE }
        for (spec in ItemCatalog.all) map[spec.drawable] = decode(resources, spec.drawable, opts)
        for (id in Sprites.all) map[id] = decode(resources, id, opts)
    }

    private fun decode(resources: Resources, id: Int, opts: BitmapFactory.Options): ImageBitmap {
        val bitmap = BitmapFactory.decodeResource(resources, id, opts)
            ?: error("Could not decode drawable ${resources.getResourceEntryName(id)} (0x${id.toString(16)})")
        return bitmap.asImageBitmap()
    }

    operator fun get(id: Int): ImageBitmap = map.getValue(id)
}

@Composable
fun GameScreen(settings: Settings, sounds: Sounds) {
    val context = LocalContext.current
    val world = remember { GameWorld() }
    val sprites = remember { SpriteCache(context.resources) }
    var frame by remember { mutableLongStateOf(0L) }
    var showParent by remember { mutableStateOf(false) }

    LaunchedEffect(settings.easyMode) { world.easyMode = settings.easyMode }
    LaunchedEffect(settings.sound) { sounds.enabled = settings.sound }
    val palette = Palettes.all[settings.paletteIndex]

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, MAX_DT)
                last = now
                world.step(dt)
                while (world.sounds.isNotEmpty()) sounds.play(world.sounds.removeFirst())
                frame++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(SKY_TOP)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { world.resize(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = HashSet<Long>()
                            for (change in event.changes) {
                                val id = change.id.value
                                val pos = change.position
                                when {
                                    change.changedToDownIgnoreConsumed() -> world.pointerDown(id, pos.x, pos.y)
                                    change.changedToUpIgnoreConsumed() -> world.pointerUp(id)
                                    change.pressed -> world.pointerMove(id, pos.x, pos.y)
                                }
                                if (change.pressed) pressed.add(id)
                                change.consume()
                            }
                            world.releaseAllExcept(pressed)
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION")
            frame // read each frame so the canvas redraws
            drawScene(world, palette, sprites)
        }

        ParentGear(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            onOpen = { showParent = true },
        )

        if (showParent) {
            ParentDialog(settings = settings, onDismiss = { showParent = false })
        }
    }
}

/** A gear that opens the parent settings only after a long hold, so a toddler is unlikely to trigger it. */
@Composable
private fun ParentGear(modifier: Modifier, onOpen: () -> Unit) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0x33000000))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val releasedEarly = withTimeoutOrNull(PARENT_HOLD_MS) { waitForUpOrCancellation(); true }
                    if (releasedEarly == null) onOpen()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Settings, contentDescription = "Parent settings", tint = Color(0x99FFFFFF), modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ParentDialog(settings: Settings, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Parent settings") },
        text = {
            Column {
                SettingRow("Easy mode (tap to bin)", settings.easyMode) { settings.updateEasyMode(it) }
                SettingRow("Sound", settings.sound) { settings.updateSound(it) }
                Spacer(Modifier.height(12.dp))
                Text("Bin colours", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((i, p) in Palettes.all.withIndex()) {
                        PaletteSwatch(p, selected = i == settings.paletteIndex) { settings.updatePalette(i) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Any gamepad button also swaps colours. To keep the game up, pin it from the Recents screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.Black else Color.Gray, CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        for (bin in palette.bins) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(bin.body)))
        }
    }
}

// ------------------------------------------------------------------ drawing

private fun DrawScope.drawScene(world: GameWorld, palette: Palette, sprites: SpriteCache) {
    val w = size.width
    val h = size.height
    if (world.width <= 0f) return
    drawBackground(world, w, h)

    val gaze = world.gazeTarget()
    for (bin in world.bins) {
        val colors = palette.bins[bin.index % palette.bins.size]
        val sinking = world.items.filter { it.state == ItemState.SINKING && it.targetBin === bin }
        drawBin(bin, colors, gaze, sinking, sprites)
    }

    for (item in world.items) {
        if (item.state == ItemState.SINKING) continue
        drawShadow(item, world.groundY)
    }
    for (item in world.items) {
        if (item.state == ItemState.SINKING) continue
        drawItem(item, sprites)
    }
    drawParticles(world.particles, sprites)
}

private fun DrawScope.drawBackground(world: GameWorld, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(0f to SKY_TOP, 1f to SKY_BOTTOM, startY = 0f, endY = world.groundY))
    val sunR = h * SUN_R_FRAC
    drawCircle(SUN_GLOW, sunR * 1.6f, Offset(w * SUN_X_FRAC, h * SUN_Y_FRAC))
    drawCircle(SUN, sunR, Offset(w * SUN_X_FRAC, h * SUN_Y_FRAC))
    for (i in 0 until CLOUD_COUNT) {
        val cloudW = h * (0.18f + 0.05f * i)
        val span = w + cloudW * 2
        val x = ((w * 0.3f * i + world.time * w * CLOUD_SPEED_FRAC * (1f + 0.3f * i)) % span) - cloudW
        val y = h * (0.12f + 0.11f * i)
        val r = cloudW * 0.22f
        drawCircle(CLOUD, r, Offset(x, y))
        drawCircle(CLOUD, r * 1.3f, Offset(x + r * 1.1f, y - r * 0.3f))
        drawCircle(CLOUD, r, Offset(x + r * 2.3f, y))
        drawRect(CLOUD, Offset(x, y), Size(r * 2.3f, r))
    }
    val gy = world.groundY
    drawRect(DIRT, Offset(0f, gy), Size(w, h - gy))
    drawRect(GRASS, Offset(0f, gy), Size(w, h * GRASS_H_FRAC))
    drawRect(GRASS_DARK, Offset(0f, gy + h * GRASS_H_FRAC * 0.8f), Size(w, h * GRASS_H_FRAC * 0.2f))
}

private fun DrawScope.drawBin(bin: Bin, colors: BinColors, gaze: Item?, sinking: List<Item>, sprites: SpriteCache) {
    val body = Color(colors.body)
    val lid = Color(colors.lid)
    val label = Color(colors.label)
    val bw = bin.width
    val bh = bin.height
    val pivot = Offset(bin.centerX, bin.bottom)

    // Capture bounce: squash then a damped wobble. Tap: rotational wiggle.
    val tc = bin.sinceCapture
    val squash = if (tc < 1f) SQUASH_AMOUNT * exp(-5f * tc) * cos(18f * tc) else 0f
    val tw = bin.sinceWiggle
    val wiggle = if (tw < 1.2f) WIGGLE_DEG * exp(-3f * tw) * sin(25f * tw) else 0f
    val lidFlap = if (tc < 0.8f) 1f - LID_FLAP_AMOUNT * exp(-4f * tc) * sin(14f * tc).coerceAtLeast(0f) else 1f

    withTransform({
        rotate(wiggle, pivot)
        scale(1f + squash, 1f - squash, pivot)
    }) {
        // Wheels (behind the body).
        val wr = bh * WHEEL_R_FRAC
        val wheelY = bin.bottom - wr * 0.55f
        drawRect(WHEEL, Offset(bin.left + bw * WHEEL_X_FRAC, wheelY - wr * 0.15f), Size(bw * (1f - 2 * WHEEL_X_FRAC), wr * 0.3f))
        for (wx in listOf(bin.left + bw * WHEEL_X_FRAC, bin.right - bw * WHEEL_X_FRAC)) {
            drawCircle(WHEEL, wr, Offset(wx, wheelY))
            drawCircle(HUB, wr * 0.4f, Offset(wx, wheelY))
        }

        // Open lid, hinged at the back top edge, standing up behind the body.
        val lip = bh * BIN_LIP_FRAC
        val lidH = bh * LID_H_FRAC * lidFlap
        val overhang = bw * LID_OVERHANG_FRAC
        val lidTopHalf = bw * LID_TOP_W_FRAC / 2
        val lidPath = Path().apply {
            moveTo(bin.left - overhang, bin.top)
            lineTo(bin.centerX - lidTopHalf, bin.top - lip - lidH)
            lineTo(bin.centerX + lidTopHalf, bin.top - lip - lidH)
            lineTo(bin.right + overhang, bin.top)
            close()
        }
        drawPath(lidPath, lid)
        // Inner panel of the lid (its underside faces us while open).
        val panelInset = bw * 0.1f
        val panelPath = Path().apply {
            moveTo(bin.left + panelInset, bin.top - lip - lidH * 0.18f)
            lineTo(bin.centerX - lidTopHalf + panelInset, bin.top - lip - lidH * 0.82f)
            lineTo(bin.centerX + lidTopHalf - panelInset, bin.top - lip - lidH * 0.82f)
            lineTo(bin.right - panelInset, bin.top - lip - lidH * 0.18f)
            close()
        }
        drawPath(panelPath, Color(colors.label).copy(alpha = 0.3f))

        // Dark interior showing through the mouth, then anything sinking in (only the part above the rim).
        drawRoundRect(INTERIOR, Offset(bin.left - overhang * 0.5f, bin.top - lip), Size(bw + overhang, lip * 2), CornerRadius(lip))
        if (sinking.isNotEmpty()) {
            clipRect(bin.left - bw, 0f, bin.right + bw, bin.top) {
                for (item in sinking) drawItem(item, sprites)
            }
        }

        // Body: slightly tapered toward the bottom.
        val taper = bw * BIN_TAPER_FRAC
        val path = Path().apply {
            moveTo(bin.left, bin.top)
            lineTo(bin.right, bin.top)
            lineTo(bin.right - taper, bin.bottom - bw * 0.06f)
            quadraticTo(bin.right - taper, bin.bottom, bin.right - taper - bw * 0.06f, bin.bottom)
            lineTo(bin.left + taper + bw * 0.06f, bin.bottom)
            quadraticTo(bin.left + taper, bin.bottom, bin.left + taper, bin.bottom - bw * 0.06f)
            close()
        }
        drawPath(path, body)
        // Rim and side shading.
        drawRect(lid, Offset(bin.left, bin.top), Size(bw, lip * 0.8f))
        drawRect(Color.Black.copy(alpha = 0.12f), Offset(bin.right - bw * 0.16f, bin.top), Size(bw * 0.16f - taper * 0.5f, bh))
        drawRect(Color.White.copy(alpha = 0.12f), Offset(bin.left + taper * 0.5f, bin.top + lip), Size(bw * 0.07f, bh - lip))

        // Label with a symbol.
        val labelW = bw * LABEL_W_FRAC
        val labelH = bh * LABEL_H_FRAC
        val labelTop = bin.top + bh * LABEL_Y_FRAC
        drawRoundRect(label, Offset(bin.centerX - labelW / 2, labelTop), Size(labelW, labelH), CornerRadius(labelH * 0.2f))
        val symbol = when (bin.index) {
            0 -> Sprites.RECYCLE
            2 -> Sprites.SEEDLING
            else -> 0
        }
        if (symbol != 0) {
            val s = labelH * 0.8f
            drawSprite(sprites[symbol], Offset(bin.centerX, labelTop + labelH / 2), s, 0f, 1f)
        }

        drawFace(bin, gaze, bw, bh)
    }
}

private fun DrawScope.drawFace(bin: Bin, gaze: Item?, bw: Float, bh: Float) {
    val eyeY = bin.top + bh * EYE_Y_FRAC
    val rx = bw * EYE_RX_FRAC
    val ry = bw * EYE_RY_FRAC
    val blink = if (bin.blinkPhase > 0f) 0.1f else 1f
    val travel = bw * PUPIL_TRAVEL_FRAC
    for (side in listOf(-1f, 1f)) {
        val ex = bin.centerX + side * bw * EYE_DX_FRAC
        if (bin.happy) {
            // Happy eyes: upside-down arcs.
            drawArc(
                PUPIL, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(ex - rx, eyeY - ry * 0.6f), size = Size(rx * 2, ry * 1.4f),
                style = Stroke(width = bw * 0.035f),
            )
            continue
        }
        drawOval(EYE_WHITE, Offset(ex - rx, eyeY - ry * blink), Size(rx * 2, ry * 2 * blink))
        if (blink < 1f) continue
        var px = ex
        var py = eyeY
        if (gaze != null) {
            val dx = gaze.x - ex
            val dy = gaze.y - eyeY
            val d = hypot(dx, dy)
            if (d > 1f) {
                px += dx / d * travel
                py += dy / d * travel
            }
        }
        drawCircle(PUPIL, bw * PUPIL_R_FRAC, Offset(px, py))
        drawCircle(EYE_WHITE, bw * PUPIL_R_FRAC * 0.35f, Offset(px - bw * 0.012f, py - bw * 0.014f))
    }
    val mouthY = bin.top + bh * MOUTH_Y_FRAC
    val mouthW = bw * 0.26f
    if (bin.happy) {
        drawArc(MOUTH, 0f, 180f, true, Offset(bin.centerX - mouthW / 2, mouthY - mouthW * 0.35f), Size(mouthW, mouthW * 0.7f))
        drawArc(TONGUE, 0f, 180f, true, Offset(bin.centerX - mouthW * 0.25f, mouthY), Size(mouthW * 0.5f, mouthW * 0.35f))
    } else {
        drawArc(
            MOUTH, 20f, 140f, false,
            Offset(bin.centerX - mouthW / 2, mouthY - mouthW * 0.3f), Size(mouthW, mouthW * 0.5f),
            style = Stroke(width = bw * 0.03f),
        )
    }
}

private fun DrawScope.drawShadow(item: Item, groundY: Float) {
    val heightAbove = (groundY - item.radius - item.y).coerceAtLeast(0f)
    val k = (1f - heightAbove / (groundY * 1.2f)).coerceIn(0.3f, 1f)
    val rx = item.radius * 0.9f * k
    val ry = item.radius * 0.22f * k
    drawOval(SHADOW.copy(alpha = 0.2f * k), Offset(item.x - rx, groundY - ry), Size(rx * 2, ry * 2))
}

private fun DrawScope.drawItem(item: Item, sprites: SpriteCache) {
    drawSprite(sprites[item.spec.drawable], Offset(item.x, item.y), item.radius * 2 * item.scale, item.rotation, 1f)
}

private fun DrawScope.drawSprite(image: ImageBitmap, center: Offset, sizePx: Float, rotationDeg: Float, alpha: Float) {
    val half = sizePx / 2
    rotate(rotationDeg, center) {
        drawImage(
            image = image,
            dstOffset = IntOffset((center.x - half).toInt(), (center.y - half).toInt()),
            dstSize = IntSize(sizePx.toInt(), sizePx.toInt()),
            alpha = alpha,
        )
    }
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun DrawScope.drawParticles(particles: Particles, sprites: SpriteCache) {
    for (p in particles.list) {
        val t = p.t
        when (p.kind) {
            ParticleKind.CONFETTI -> {
                val alpha = if (t > 0.7f) (1f - t) / 0.3f else 1f
                rotate(p.rotation, Offset(p.x, p.y)) {
                    drawRect(Color(p.color).copy(alpha = alpha), Offset(p.x - p.size / 2, p.y - p.size * 0.3f), Size(p.size, p.size * 0.6f))
                }
            }
            ParticleKind.SPRITE -> {
                val alpha = if (t > 0.6f) (1f - t) / 0.4f else 1f
                drawSprite(sprites[p.sprite], Offset(p.x, p.y), p.size * (1f - 0.4f * t), p.rotation, alpha)
            }
            ParticleKind.RING -> {
                val radius = p.size * p.age
                drawCircle(Color(p.color).copy(alpha = 1f - t), radius, Offset(p.x, p.y), style = Stroke(width = p.size * 0.06f * (1f - t) + 2f))
            }
            ParticleKind.POP -> {
                val grow = easeOutBack(min(1f, t * 3f))
                val alpha = if (t > 0.65f) (1f - t) / 0.35f else 1f
                drawSprite(sprites[p.sprite], Offset(p.x, p.y), p.size * grow, p.rotation + sin(p.age * 12f) * 6f, alpha)
            }
        }
    }
}
