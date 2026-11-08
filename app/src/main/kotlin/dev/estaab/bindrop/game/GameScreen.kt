package dev.estaab.bindrop.game

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.estaab.bindrop.Settings
import dev.estaab.bindrop.audio.Sounds
import dev.estaab.bindrop.render3d.FrameSnapshot
import dev.estaab.bindrop.render3d.StageConfigChooser
import dev.estaab.bindrop.render3d.StageRenderer
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_DT = 0.05f
private const val PARENT_HOLD_MS = 1500L
private const val GLES_VERSION = 3

/** Holds the GL view once Compose has created it, so the frame loop and lifecycle can reach it. */
private class GlViewHolder {
    var view: GLSurfaceView? = null
}

@Composable
fun GameScreen(settings: Settings, sounds: Sounds, onPinChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val world = remember { GameWorld() }
    val renderer = remember { StageRenderer(context.resources) }
    val glView = remember { GlViewHolder() }
    var showParent by remember { mutableStateOf(false) }

    LaunchedEffect(settings.easyMode) { world.easyMode = settings.easyMode }
    LaunchedEffect(settings.sound) { sounds.enabled = settings.sound }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, MAX_DT)
                last = now
                world.step(dt)
                while (world.sounds.isNotEmpty()) sounds.play(world.sounds.removeFirst())
                while (world.events.isNotEmpty()) {
                    when (val event = world.events.removeFirst()) {
                        GameEvent.CyclePalette -> settings.cyclePalette()
                        is GameEvent.Speak -> {
                            val colors = Palettes.all[settings.paletteIndex].bins[event.bin]
                            sounds.say(event.bin, colors.spoken)
                        }
                    }
                }
                // The GL thread draws an immutable copy; it never touches the live world.
                renderer.publish(FrameSnapshot.of(world, Palettes.all[settings.paletteIndex]))
                glView.view?.requestRender()
            }
        }
    }

    // GLSurfaceView must be paused and resumed with the activity.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glView.view?.onPause()
                Lifecycle.Event.ON_RESUME -> glView.view?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color(Scenes.all.first().skyTop))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(GLES_VERSION)
                    setEGLConfigChooser(StageConfigChooser())
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glView.view = this
                }
            },
        )
        // Touch layer over the 3D view. The stage camera maps the play plane 1:1 onto the screen, so the world's
        // pixel coordinates are used exactly as in the 2D version.
        Box(
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
                },
        )

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
