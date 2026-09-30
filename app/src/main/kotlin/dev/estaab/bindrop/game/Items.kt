package dev.estaab.bindrop.game

import dev.estaab.bindrop.R

/** Which real-world bin an item belongs in. Only used for variety; every bin accepts every item. */
enum class ItemKind { COMPOST, RECYCLE, LANDFILL }

data class ItemSpec(val name: String, val drawable: Int, val kind: ItemKind)

object ItemCatalog {
    val all: List<ItemSpec> = listOf(
        ItemSpec("banana", R.drawable.emoji_banana, ItemKind.COMPOST),
        ItemSpec("apple", R.drawable.emoji_apple, ItemKind.COMPOST),
        ItemSpec("carrot", R.drawable.emoji_carrot, ItemKind.COMPOST),
        ItemSpec("broccoli", R.drawable.emoji_broccoli, ItemKind.COMPOST),
        ItemSpec("pizza", R.drawable.emoji_pizza, ItemKind.COMPOST),
        ItemSpec("leaf", R.drawable.emoji_leaf, ItemKind.COMPOST),
        ItemSpec("bread", R.drawable.emoji_bread, ItemKind.COMPOST),
        ItemSpec("cookie", R.drawable.emoji_cookie, ItemKind.COMPOST),
        ItemSpec("newspaper", R.drawable.emoji_newspaper, ItemKind.RECYCLE),
        ItemSpec("can", R.drawable.emoji_can, ItemKind.RECYCLE),
        ItemSpec("box", R.drawable.emoji_box, ItemKind.RECYCLE),
        ItemSpec("milk", R.drawable.emoji_milk, ItemKind.RECYCLE),
        ItemSpec("cup", R.drawable.emoji_cup, ItemKind.RECYCLE),
        ItemSpec("bottle", R.drawable.emoji_bottle, ItemKind.RECYCLE),
        ItemSpec("envelope", R.drawable.emoji_envelope, ItemKind.RECYCLE),
        ItemSpec("sock", R.drawable.emoji_sock, ItemKind.LANDFILL),
        ItemSpec("shoe", R.drawable.emoji_shoe, ItemKind.LANDFILL),
        ItemSpec("balloon", R.drawable.emoji_balloon, ItemKind.LANDFILL),
        ItemSpec("sponge", R.drawable.emoji_sponge, ItemKind.LANDFILL),
        ItemSpec("bulb", R.drawable.emoji_bulb, ItemKind.LANDFILL),
        ItemSpec("toothbrush", R.drawable.emoji_toothbrush, ItemKind.LANDFILL),
        ItemSpec("candy", R.drawable.emoji_candy, ItemKind.LANDFILL),
    )
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
