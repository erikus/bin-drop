package dev.estaab.bindrop.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.estaab.bindrop.R
import dev.estaab.bindrop.game.Sfx

private const val MAX_STREAMS = 6
private const val VOLUME = 0.9f

/** SoundPool wrapper for the game's short clips. Call [release] when the activity is destroyed. */
class Sounds(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.POP to pool.load(context, R.raw.sfx_pop, 1),
        Sfx.DROP to pool.load(context, R.raw.sfx_drop, 1),
        Sfx.CHIME to pool.load(context, R.raw.sfx_chime, 1),
        Sfx.BOING to pool.load(context, R.raw.sfx_boing, 1),
        Sfx.BIGWIN to pool.load(context, R.raw.sfx_bigwin, 1),
        Sfx.MAGIC to pool.load(context, R.raw.sfx_magic, 1),
    )

    var enabled = true

    fun play(sfx: Sfx) {
        if (!enabled) return
        val id = ids[sfx] ?: return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    fun release() = pool.release()
}
