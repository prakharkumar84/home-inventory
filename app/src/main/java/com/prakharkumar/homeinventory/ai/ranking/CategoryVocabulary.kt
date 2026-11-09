package com.prakharkumar.homeinventory.ai.ranking

/**
 * Local, offline category vocabulary. Also doubles as the candidate list for a
 * future image-text matcher (spec 5.3) without any network call.
 */
object CategoryVocabulary {

    val categories = listOf(
        "Cables & chargers", "Electronics", "Tools", "Stationery", "Kitchen",
        "Clothing", "Documents", "Medicine", "Toys", "Sports", "Batteries",
        "Cleaning", "Hardware & fasteners", "Bags & luggage", "Jewellery",
        "Books", "Shoes", "Decor", "Garden", "Other"
    )

    private val keywordMap: Map<String, String> = mapOf(
        "cable" to "Cables & chargers", "charger" to "Cables & chargers",
        "wire" to "Cables & chargers", "adapter" to "Cables & chargers",
        "usb" to "Cables & chargers", "hdmi" to "Cables & chargers",
        "earphone" to "Electronics", "headphone" to "Electronics",
        "mouse" to "Electronics", "keyboard" to "Electronics",
        "phone" to "Electronics", "remote" to "Electronics", "camera" to "Electronics",
        "battery" to "Batteries",
        "screwdriver" to "Tools", "hammer" to "Tools", "plier" to "Tools",
        "wrench" to "Tools", "drill" to "Tools", "tape" to "Tools",
        "screw" to "Hardware & fasteners", "nail" to "Hardware & fasteners",
        "bolt" to "Hardware & fasteners", "nut" to "Hardware & fasteners",
        "pen" to "Stationery", "pencil" to "Stationery", "notebook" to "Stationery",
        "paper" to "Stationery", "marker" to "Stationery", "stapler" to "Stationery",
        "bottle" to "Kitchen", "cup" to "Kitchen", "plate" to "Kitchen",
        "spoon" to "Kitchen", "knife" to "Kitchen", "pan" to "Kitchen",
        "shirt" to "Clothing", "trouser" to "Clothing", "jacket" to "Clothing",
        "sock" to "Clothing", "scarf" to "Clothing",
        "shoe" to "Shoes", "sandal" to "Shoes", "sneaker" to "Shoes",
        "book" to "Books", "magazine" to "Books",
        "passport" to "Documents", "certificate" to "Documents", "file" to "Documents",
        "medicine" to "Medicine", "tablet" to "Medicine", "syrup" to "Medicine",
        "toy" to "Toys", "doll" to "Toys", "puzzle" to "Toys",
        "ball" to "Sports", "racket" to "Sports", "bat" to "Sports",
        "bag" to "Bags & luggage", "suitcase" to "Bags & luggage",
        "ring" to "Jewellery", "necklace" to "Jewellery", "watch" to "Jewellery",
        "soap" to "Cleaning", "detergent" to "Cleaning", "brush" to "Cleaning"
    )

    fun categoryFor(normalizedLabel: String): String? =
        keywordMap.entries.firstOrNull { (key, _) -> normalizedLabel.contains(key) }?.value
}
