package dev.estaab.bindrop.game

/** Colours for one bin, as ARGB longs (converted to Compose colours at draw time). */
data class BinColors(val name: String, val body: Long, val lid: Long, val label: Long)

data class Palette(val name: String, val bins: List<BinColors>)

object Palettes {
    val all: List<Palette> = listOf(
        Palette(
            "Recology",
            listOf(
                BinColors("Blue", 0xFF1E5EBF, 0xFF174A99, 0xFFA9C6F2),
                BinColors("Grey", 0xFF7A7D80, 0xFF5C5F62, 0xFFD2D5D8),
                BinColors("Green", 0xFF2F8F3E, 0xFF226E2F, 0xFFB0E2B6),
            ),
        ),
        Palette(
            "Sunset",
            listOf(
                BinColors("Red", 0xFFD93B3B, 0xFFA82727, 0xFFF6B8B8),
                BinColors("Orange", 0xFFF08A24, 0xFFC26A12, 0xFFFBD6AC),
                BinColors("Yellow", 0xFFE8C21E, 0xFFB89710, 0xFFF9ECA6),
            ),
        ),
        Palette(
            "Candy",
            listOf(
                BinColors("Pink", 0xFFE85A9B, 0xFFB93C78, 0xFFF7BFD9),
                BinColors("Purple", 0xFF7B4FBF, 0xFF5B3696, 0xFFCDB9EE),
                BinColors("Teal", 0xFF23A5A0, 0xFF177C78, 0xFFB0E6E3),
            ),
        ),
        Palette(
            "Ocean",
            listOf(
                BinColors("Navy", 0xFF243B7A, 0xFF17285A, 0xFFB3BEE0),
                BinColors("Aqua", 0xFF35B8D9, 0xFF2489A3, 0xFFB8E9F4),
                BinColors("Sand", 0xFFD9B87C, 0xFFB0925A, 0xFFF2E4C4),
            ),
        ),
        Palette(
            "Forest",
            listOf(
                BinColors("Brown", 0xFF7B4F2E, 0xFF5A3820, 0xFFD8BFA8),
                BinColors("Lime", 0xFF8CC63F, 0xFF669529, 0xFFDDEFC0),
                BinColors("Olive", 0xFF6B7F2E, 0xFF4E5D20, 0xFFCFD8AE),
            ),
        ),
    )
}
