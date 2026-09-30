package dev.estaab.bindrop.game

/** What hangs in the sky. */
enum class SkyObject { SUN, LOW_SUN, MOON, RAINBOW, NONE }

/** Ambient animated decoration drawn over the sky. */
enum class Ambient { CLOUDS, STARS, SNOW, FIREFLIES, BUBBLES, PETALS }

/** A background look. Colors are ARGB longs so this file stays free of Android types. */
data class Scene(
    val name: String,
    val skyTop: Long,
    val skyBottom: Long,
    val grass: Long,
    val grassDark: Long,
    val dirt: Long,
    val cloud: Long,
    val sky: SkyObject,
    val ambient: Ambient,
)

/** Rotated through as the toddler fills bins, for novelty. Order is the rotation order. */
object Scenes {
    val all: List<Scene> = listOf(
        Scene("Day", 0xFF8ED6FF, 0xFFDDF4FF, 0xFF7BCB5B, 0xFF5FAE43, 0xFFA8794E, 0xFFFFFFFF, SkyObject.SUN, Ambient.CLOUDS),
        Scene("Sunset", 0xFF5B3A8C, 0xFFFFA65C, 0xFF6BAA4A, 0xFF4E8A36, 0xFF8C6240, 0xFFFFD1B0, SkyObject.LOW_SUN, Ambient.CLOUDS),
        Scene("Night", 0xFF0B1A3A, 0xFF27407A, 0xFF3F7A46, 0xFF2E5C34, 0xFF4E3B2A, 0xFFFFFFFF, SkyObject.MOON, Ambient.STARS),
        Scene("Snow", 0xFFB9D4E8, 0xFFEFF6FB, 0xFFF4F9FF, 0xFFCFE0EE, 0xFF8FA6B8, 0xFFFFFFFF, SkyObject.NONE, Ambient.SNOW),
        Scene("Spring", 0xFF9FE0FF, 0xFFFFF0F6, 0xFF8BD66B, 0xFF6AB94E, 0xFFA8794E, 0xFFFFFFFF, SkyObject.RAINBOW, Ambient.PETALS),
        Scene("Ocean", 0xFF0E6F9E, 0xFF5CCBE8, 0xFFE9D8A6, 0xFFD2BC85, 0xFFB89A63, 0xFFFFFFFF, SkyObject.NONE, Ambient.BUBBLES),
        Scene("Dusk", 0xFF1F2F5C, 0xFF6D5BA8, 0xFF4F8F4A, 0xFF3A6E36, 0xFF5C4634, 0xFFFFFFFF, SkyObject.MOON, Ambient.FIREFLIES),
    )
}
