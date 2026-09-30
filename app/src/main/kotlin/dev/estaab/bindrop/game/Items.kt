package dev.estaab.bindrop.game

import dev.estaab.bindrop.R

/** Which real-world bin an item belongs in. Only used for variety; every bin accepts every item. */
enum class ItemKind { COMPOST, RECYCLE, LANDFILL }

data class ItemSpec(val name: String, val drawable: Int, val kind: ItemKind)

private fun compost(name: String, drawable: Int) = ItemSpec(name, drawable, ItemKind.COMPOST)
private fun recycle(name: String, drawable: Int) = ItemSpec(name, drawable, ItemKind.RECYCLE)
private fun landfill(name: String, drawable: Int) = ItemSpec(name, drawable, ItemKind.LANDFILL)

/** One item set per scene, keyed by scene name (see [Scenes]); every scene must have an entry. */
object ItemCatalog {
    val everyday: List<ItemSpec> = listOf(
        compost("banana", R.drawable.emoji_banana),
        compost("apple", R.drawable.emoji_apple),
        compost("carrot", R.drawable.emoji_carrot),
        compost("broccoli", R.drawable.emoji_broccoli),
        compost("pizza", R.drawable.emoji_pizza),
        compost("leaf", R.drawable.emoji_leaf),
        compost("bread", R.drawable.emoji_bread),
        compost("cookie", R.drawable.emoji_cookie),
        recycle("newspaper", R.drawable.emoji_newspaper),
        recycle("can", R.drawable.emoji_can),
        recycle("box", R.drawable.emoji_box),
        recycle("milk", R.drawable.emoji_milk),
        recycle("cup", R.drawable.emoji_cup),
        recycle("bottle", R.drawable.emoji_bottle),
        recycle("envelope", R.drawable.emoji_envelope),
        landfill("sock", R.drawable.emoji_sock),
        landfill("shoe", R.drawable.emoji_shoe),
        landfill("balloon", R.drawable.emoji_balloon),
        landfill("sponge", R.drawable.emoji_sponge),
        landfill("bulb", R.drawable.emoji_bulb),
        landfill("toothbrush", R.drawable.emoji_toothbrush),
        landfill("candy", R.drawable.emoji_candy),
    )

    val picnic: List<ItemSpec> = listOf(
        compost("hotdog", R.drawable.emoji_hotdog),
        compost("watermelon", R.drawable.emoji_watermelon),
        compost("icecream", R.drawable.emoji_icecream),
        compost("corn", R.drawable.emoji_corn),
        compost("popcorn", R.drawable.emoji_popcorn),
        compost("burger", R.drawable.emoji_burger),
        compost("fries", R.drawable.emoji_fries),
        recycle("lemonade", R.drawable.emoji_lemonade),
        recycle("can", R.drawable.emoji_can),
    )

    val bedtime: List<ItemSpec> = listOf(
        landfill("teddy", R.drawable.emoji_teddy),
        recycle("book", R.drawable.emoji_book),
        landfill("flashlight", R.drawable.emoji_flashlight),
        landfill("owl", R.drawable.emoji_owl),
        landfill("bat", R.drawable.emoji_bat),
        landfill("candle", R.drawable.emoji_candle),
        compost("cookie", R.drawable.emoji_cookie),
        recycle("milk", R.drawable.emoji_milk),
        landfill("sock", R.drawable.emoji_sock),
    )

    val winter: List<ItemSpec> = listOf(
        landfill("snowman", R.drawable.emoji_snowman),
        landfill("mittens", R.drawable.emoji_mittens),
        landfill("scarf", R.drawable.emoji_scarf),
        recycle("cocoa", R.drawable.emoji_cocoa),
        landfill("snowflake", R.drawable.emoji_snowflake),
        landfill("penguin", R.drawable.emoji_penguin),
        landfill("sled", R.drawable.emoji_sled),
        landfill("skate", R.drawable.emoji_skate),
    )

    val garden: List<ItemSpec> = listOf(
        compost("tulip", R.drawable.emoji_tulip),
        compost("sunflower", R.drawable.emoji_sunflower),
        compost("blossom", R.drawable.emoji_blossom),
        landfill("butterfly", R.drawable.emoji_butterfly),
        landfill("bee", R.drawable.emoji_bee),
        landfill("ladybug", R.drawable.emoji_ladybug),
        compost("strawberry", R.drawable.emoji_strawberry),
        landfill("bird", R.drawable.emoji_bird),
        compost("carrot", R.drawable.emoji_carrot),
    )

    val beach: List<ItemSpec> = listOf(
        landfill("shell", R.drawable.emoji_shell),
        landfill("fish", R.drawable.emoji_fish),
        landfill("crab", R.drawable.emoji_crab),
        landfill("octopus", R.drawable.emoji_octopus),
        recycle("bucket", R.drawable.emoji_bucket),
        landfill("sailboat", R.drawable.emoji_sailboat),
        landfill("whale", R.drawable.emoji_whale),
        landfill("tropicalfish", R.drawable.emoji_tropicalfish),
        recycle("bottle", R.drawable.emoji_bottle),
    )

    val camping: List<ItemSpec> = listOf(
        landfill("lantern", R.drawable.emoji_lantern),
        landfill("tent", R.drawable.emoji_tent),
        landfill("guitar", R.drawable.emoji_guitar),
        landfill("frog", R.drawable.emoji_frog),
        compost("mushroom", R.drawable.emoji_mushroom),
        landfill("hedgehog", R.drawable.emoji_hedgehog),
        landfill("cricket", R.drawable.emoji_cricket),
        landfill("owl", R.drawable.emoji_owl),
        landfill("flashlight", R.drawable.emoji_flashlight),
    )

    val byScene: Map<String, List<ItemSpec>> = mapOf(
        "Day" to everyday,
        "Sunset" to picnic,
        "Night" to bedtime,
        "Snow" to winter,
        "Spring" to garden,
        "Ocean" to beach,
        "Dusk" to camping,
    )

    /** Every item any scene can spawn, for preloading. */
    val all: List<ItemSpec> = byScene.values.flatten().distinctBy { it.drawable }

    fun forScene(scene: Scene): List<ItemSpec> =
        byScene[scene.name] ?: error("No item set for scene '${scene.name}'")
}

/** Decorative sprites used by effects and on the bins. */
object Sprites {
    val STAR: Int = R.drawable.emoji_star
    val PARTY: Int = R.drawable.emoji_party
    val SPARKLES: Int = R.drawable.emoji_sparkles
    val HEART: Int = R.drawable.emoji_heart
    val RECYCLE: Int = R.drawable.emoji_recycle
    val SEEDLING: Int = R.drawable.emoji_seedling

    val all: List<Int> = listOf(STAR, PARTY, SPARKLES, HEART, RECYCLE, SEEDLING)
}
