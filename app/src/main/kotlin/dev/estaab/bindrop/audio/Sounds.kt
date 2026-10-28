package dev.estaab.bindrop.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.estaab.bindrop.R
import dev.estaab.bindrop.game.ColorWord
import dev.estaab.bindrop.game.Sfx

private const val MAX_STREAMS = 8
private const val VOLUME = 0.9f
private const val VOICE_VOLUME = 1f
private const val SFX_PRIORITY = 1
private const val VOICE_PRIORITY = 2        // a voice is never cut off to make room for a sound effect

/**
 * Each bin's voice saying each colour word, indexed by bin. Clips come from tools/make_voices.py; one voice per
 * bin so the bins sound like three different characters whatever colours they wear.
 */
private val VOICE_CLIPS: List<Map<ColorWord, Int>> = listOf(
    mapOf(
        ColorWord.BLUE to R.raw.voice_0_blue,
        ColorWord.RED to R.raw.voice_0_red,
        ColorWord.PINK to R.raw.voice_0_pink,
        ColorWord.BROWN to R.raw.voice_0_brown,
        ColorWord.GREY to R.raw.voice_0_grey,
        ColorWord.ORANGE to R.raw.voice_0_orange,
        ColorWord.PURPLE to R.raw.voice_0_purple,
        ColorWord.GREEN to R.raw.voice_0_green,
        ColorWord.YELLOW to R.raw.voice_0_yellow,
    ),
    mapOf(
        ColorWord.BLUE to R.raw.voice_1_blue,
        ColorWord.RED to R.raw.voice_1_red,
        ColorWord.PINK to R.raw.voice_1_pink,
        ColorWord.BROWN to R.raw.voice_1_brown,
        ColorWord.GREY to R.raw.voice_1_grey,
        ColorWord.ORANGE to R.raw.voice_1_orange,
        ColorWord.PURPLE to R.raw.voice_1_purple,
        ColorWord.GREEN to R.raw.voice_1_green,
        ColorWord.YELLOW to R.raw.voice_1_yellow,
    ),
    mapOf(
        ColorWord.BLUE to R.raw.voice_2_blue,
        ColorWord.RED to R.raw.voice_2_red,
        ColorWord.PINK to R.raw.voice_2_pink,
        ColorWord.BROWN to R.raw.voice_2_brown,
        ColorWord.GREY to R.raw.voice_2_grey,
        ColorWord.ORANGE to R.raw.voice_2_orange,
        ColorWord.PURPLE to R.raw.voice_2_purple,
        ColorWord.GREEN to R.raw.voice_2_green,
        ColorWord.YELLOW to R.raw.voice_2_yellow,
    ),
)

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

    /** [bin][word] -> SoundPool sample id. Every bin must have every word, checked once at startup. */
    private val voiceIds: List<Map<ColorWord, Int>> = VOICE_CLIPS.map { clips ->
        for (word in ColorWord.values()) requireNotNull(clips[word]) { "no voice clip for $word" }
        clips.mapValues { (_, res) -> pool.load(context, res, 1) }
    }

    var enabled = true

    fun play(sfx: Sfx) {
        if (!enabled) return
        val id = ids[sfx] ?: return
        pool.play(id, VOLUME, VOLUME, SFX_PRIORITY, 0, 1f)
    }

    /** Bin [bin] (0-based) says [word] in its own voice. */
    fun say(bin: Int, word: ColorWord) {
        if (!enabled) return
        val id = voiceIds[bin % voiceIds.size].getValue(word)
        pool.play(id, VOICE_VOLUME, VOICE_VOLUME, VOICE_PRIORITY, 0, 1f)
    }

    fun release() = pool.release()
}
