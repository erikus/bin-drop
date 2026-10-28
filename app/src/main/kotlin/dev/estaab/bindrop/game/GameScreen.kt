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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val MAX_DT = 0.05f
private const val PARENT_HOLD_MS = 1500L
private const val EMOJI_SAMPLE_SIZE = 2          // 512 px source → 256 px in memory

private val SUN = Color(0xFFFFE066)
private val SUN_GLOW = Color(0x55FFE066)
private val LOW_SUN = Color(0xFFFF8A3D)
private val LOW_SUN_GLOW = Color(0x66FFB347)
private val MOON = Color(0xFFF6F1D3)
private val MOON_GLOW = Color(0x33F6F1D3)
private val MOON_CRATER = Color(0xFFD9D2B0)
private val STAR = Color(0xFFFFFFFF)
private val SNOWFLAKE = Color(0xFFFFFFFF)
private val FIREFLY = Color(0xFFDFFF6A)
private val BUBBLE = Color(0x66FFFFFF)
private val PETAL = Color(0xFFFFB7D5)
private val RAINBOW_COLORS = listOf(0xFFFF5E5E, 0xFFFFA943, 0xFFFFE666, 0xFF7ED957, 0xFF5AB4FF, 0xFF9B7BFF).map { Color(it) }
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
private const val LOW_SUN_X_FRAC = 0.26f         // over the item zone so the bins do not hide it
private const val LOW_SUN_Y_FRAC = 0.62f         // sits on the horizon at sunset
private const val LOW_SUN_R_FRAC = 0.13f
private const val RAINBOW_R_FRAC = 0.75f         // of H, outer radius; centred below the horizon
private const val RAINBOW_BAND_FRAC = 0.035f     // of H, per colour band
private const val STAR_COUNT = 40
private const val SNOW_COUNT = 70
private const val SNOW_FALL_FRAC = 0.08f         // H/s
private const val FIREFLY_COUNT = 18
private const val BUBBLE_COUNT = 24
private const val BUBBLE_RISE_FRAC = 0.06f       // H/s
private const val PETAL_COUNT = 30
private const val PETAL_FALL_FRAC = 0.05f        // H/s
private const val PETAL_DRIFT_FRAC = 0.03f       // W/s
private const val AMBIENT_SIZE_FRAC = 0.012f     // of H, base radius of small decorations
private const val TWO_PI = (2 * Math.PI).toFloat()
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
fun GameScreen(settings: Settings, sounds: Sounds, onPinChange: (Boolean) -> Unit) {
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
                while (world.events.isNotEmpty()) {
                    when (world.events.removeFirst()) {
                        GameEvent.CYCLE_PALETTE -> settings.cyclePalette()
                    }
                }
                frame++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(Scenes.all.first().skyTop))) {
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
            ParentDialog(settings = settings, onPinChange = onPinChange, onDismiss = { showParent = false })
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
private fun ParentDialog(settings: Settings, onPinChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Parent settings") },
        text = {
            Column {
                SettingRow("Easy mode (tap to bin)", settings.easyMode) { settings.updateEasyMode(it) }
                SettingRow("Sound", settings.sound) { settings.updateSound(it) }
                SettingRow("Pin app (blocks Home & shade)", settings.pinApp, onPinChange)
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
                    "Any gamepad button, or three quick taps on a bin, also swaps colours. " +
                        "Pinning asks Android for confirmation once per launch; " +
                        "turn it off here to unpin.",
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
    val scenes = Scenes.all
    val current = scenes[world.sceneIndex]
    if (world.sceneReveal >= 1f) {
        drawScene(current, world, w, h)
        return
    }
    // Circular wipe: the previous scene stays underneath while the new one grows out of the bin.
    val previous = scenes[(world.sceneIndex + scenes.size - 1) % scenes.size]
    drawScene(previous, world, w, h)
    val maxR = hypot(max(world.sceneRevealX, w - world.sceneRevealX), max(world.sceneRevealY, h - world.sceneRevealY))
    val reveal = Path().apply { addOval(Rect(Offset(world.sceneRevealX, world.sceneRevealY), maxR * easeOutQuad(world.sceneReveal))) }
    clipPath(reveal) { drawScene(current, world, w, h) }
}

private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

private fun DrawScope.drawScene(scene: Scene, world: GameWorld, w: Float, h: Float) {
    val gy = world.groundY
    drawRect(Brush.verticalGradient(0f to Color(scene.skyTop), 1f to Color(scene.skyBottom), startY = 0f, endY = gy))
    when (scene.sky) {
        SkyObject.SUN -> drawSun(w, h)
        SkyObject.LOW_SUN -> {
            val c = Offset(w * LOW_SUN_X_FRAC, h * LOW_SUN_Y_FRAC)
            val r = h * LOW_SUN_R_FRAC
            drawCircle(LOW_SUN_GLOW, r * 1.8f, c)
            clipRect(top = 0f, bottom = gy) { drawCircle(LOW_SUN, r, c) }
        }
        SkyObject.MOON -> {
            val c = Offset(w * (1f - SUN_X_FRAC), h * SUN_Y_FRAC)
            val r = h * SUN_R_FRAC
            drawCircle(MOON_GLOW, r * 1.7f, c)
            drawCircle(MOON, r, c)
            drawCircle(MOON_CRATER, r * 0.22f, c + Offset(-r * 0.3f, -r * 0.2f))
            drawCircle(MOON_CRATER, r * 0.14f, c + Offset(r * 0.35f, r * 0.25f))
            drawCircle(MOON_CRATER, r * 0.1f, c + Offset(r * 0.05f, r * 0.45f))
        }
        SkyObject.RAINBOW -> {
            drawSun(w, h)
            val band = h * RAINBOW_BAND_FRAC
            val outer = h * RAINBOW_R_FRAC
            val centre = Offset(w * 0.5f, gy + h * 0.15f)
            clipRect(top = 0f, bottom = gy) {
                for ((i, c) in RAINBOW_COLORS.withIndex()) {
                    val r = outer - band * i - band / 2
                    drawCircle(c.copy(alpha = 0.75f), r, centre, style = Stroke(width = band))
                }
            }
        }
        SkyObject.NONE -> Unit
    }
    drawAmbient(scene.ambient, Color(scene.cloud), world.time, w, h, gy)
    drawRect(Color(scene.dirt), Offset(0f, gy), Size(w, h - gy))
    drawRect(Color(scene.grass), Offset(0f, gy), Size(w, h * GRASS_H_FRAC))
    drawRect(Color(scene.grassDark), Offset(0f, gy + h * GRASS_H_FRAC * 0.8f), Size(w, h * GRASS_H_FRAC * 0.2f))
}

private fun DrawScope.drawSun(w: Float, h: Float) {
    val sunR = h * SUN_R_FRAC
    drawCircle(SUN_GLOW, sunR * 1.6f, Offset(w * SUN_X_FRAC, h * SUN_Y_FRAC))
    drawCircle(SUN, sunR, Offset(w * SUN_X_FRAC, h * SUN_Y_FRAC))
}

/** Cheap deterministic pseudo-random in [0,1) so decorations need no stored state. */
private fun hash01(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
}

private fun DrawScope.drawAmbient(ambient: Ambient, cloud: Color, time: Float, w: Float, h: Float, gy: Float) {
    val base = h * AMBIENT_SIZE_FRAC
    when (ambient) {
        Ambient.CLOUDS -> drawClouds(time, w, h, cloud)
        Ambient.STARS -> for (i in 0 until STAR_COUNT) {
            val twinkle = 0.5f + 0.5f * sin(time * (1.5f + hash01(i, 3) * 2f) + hash01(i, 4) * TWO_PI)
            drawCircle(STAR.copy(alpha = 0.3f + 0.7f * twinkle), base * (0.3f + 0.5f * hash01(i, 5)), Offset(w * hash01(i, 1), gy * 0.7f * hash01(i, 2)))
        }
        Ambient.SNOW -> for (i in 0 until SNOW_COUNT) {
            val speed = h * SNOW_FALL_FRAC * (0.7f + 0.6f * hash01(i, 3))
            val y = (h * hash01(i, 2) + time * speed) % (gy + base * 2) - base
            val x = w * hash01(i, 1) + sin(time * 0.8f + hash01(i, 4) * TWO_PI) * base * 3
            drawCircle(SNOWFLAKE.copy(alpha = 0.85f), base * (0.35f + 0.5f * hash01(i, 5)), Offset(x, y))
        }
        Ambient.FIREFLIES -> for (i in 0 until FIREFLY_COUNT) {
            val phase = hash01(i, 4) * TWO_PI
            val x = w * hash01(i, 1) + sin(time * 0.5f + phase) * w * 0.04f
            val y = gy * (0.35f + 0.6f * hash01(i, 2)) + cos(time * 0.7f + phase) * h * 0.03f
            val glow = (0.5f + 0.5f * sin(time * (2f + hash01(i, 3) * 3f) + phase)).let { it * it }
            drawCircle(FIREFLY.copy(alpha = 0.25f * glow), base * 2.2f, Offset(x, y))
            drawCircle(FIREFLY.copy(alpha = 0.3f + 0.7f * glow), base * 0.55f, Offset(x, y))
        }
        Ambient.BUBBLES -> for (i in 0 until BUBBLE_COUNT) {
            val speed = h * BUBBLE_RISE_FRAC * (0.6f + 0.8f * hash01(i, 3))
            val span = gy + base * 6
            val y = span - ((h * hash01(i, 2) + time * speed) % span)
            val x = w * hash01(i, 1) + sin(time * 1.2f + hash01(i, 4) * TWO_PI) * base * 2
            val r = base * (0.6f + 1.4f * hash01(i, 5))
            drawCircle(BUBBLE, r, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.25f, Offset(x - r * 0.35f, y - r * 0.35f))
        }
        Ambient.PETALS -> {
            drawClouds(time, w, h, cloud)
            for (i in 0 until PETAL_COUNT) {
                val fall = h * PETAL_FALL_FRAC * (0.7f + 0.6f * hash01(i, 3))
                val y = (h * hash01(i, 2) + time * fall) % (gy + base * 2) - base
                val x = (w * hash01(i, 1) + time * w * PETAL_DRIFT_FRAC + sin(time * 1.5f + hash01(i, 4) * TWO_PI) * base * 4) % (w + base * 4) - base * 2
                val rot = time * 120f * (hash01(i, 5) - 0.5f)
                rotate(rot, Offset(x, y)) {
                    drawOval(PETAL, Offset(x - base * 1.1f, y - base * 0.6f), Size(base * 2.2f, base * 1.2f))
                }
            }
        }
    }
}

private fun DrawScope.drawClouds(time: Float, w: Float, h: Float, color: Color) {
    for (i in 0 until CLOUD_COUNT) {
        val cloudW = h * (0.18f + 0.05f * i)
        val span = w + cloudW * 2
        val x = ((w * 0.3f * i + time * w * CLOUD_SPEED_FRAC * (1f + 0.3f * i)) % span) - cloudW
        val y = h * (0.12f + 0.11f * i)
        val r = cloudW * 0.22f
        drawCircle(color, r, Offset(x, y))
        drawCircle(color, r * 1.3f, Offset(x + r * 1.1f, y - r * 0.3f))
        drawCircle(color, r, Offset(x + r * 2.3f, y))
        drawRect(color, Offset(x, y), Size(r * 2.3f, r))
    }
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
