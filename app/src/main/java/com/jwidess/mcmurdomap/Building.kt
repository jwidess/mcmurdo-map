package com.jwidess.mcmurdomap

data class Building(
    val id: String,
    val name: String,
    val photo: String?,
    val description: String?,
    val construction: String?,
    val occupancy: String?,
    val aeds: List<String>,
    val musterPoints: List<String>,
    val infoSheet: String?
) {
    val cleanId: String
        get() = id.trim()

    val displayName: String
        get() = if (name.isNotBlank()) name else "Building $id"

    val hasPhoto: Boolean
        get() = !photo.isNullOrBlank() && photo != "no-image.png"

    val hasAeds: Boolean
        get() = aeds.isNotEmpty()

    val hasInfoSheet: Boolean
        get() = !infoSheet.isNullOrBlank()
}
