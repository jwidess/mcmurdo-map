package com.jwidess.mcmurdomap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

data class SyncInfo(
    val lastSyncedFormatted: String = "Offline Packaged",
    val sourceUrl: String = "https://www.mcmurdo.usap.gov/map/",
    val totalPhotos: Int = 0,
    val totalDocs: Int = 0
)

object BuildingRepository {

    private var buildings: List<Building> = emptyList()
    private val buildingsById = mutableMapOf<String, Building>()
    private var cachedSyncInfo: SyncInfo? = null

    // Tag and alias dictionary for station search (Item 4)
    private val aliases = mapOf(
        "galley" to listOf("B155", "B076", "B107", "B108"),
        "dining" to listOf("B155", "B076", "B107", "B108"),
        "cafeteria" to listOf("B155", "B076", "B107"),
        "food" to listOf("B155", "B076", "B107", "B108"),
        "smoke" to listOf("B700", "B701", "B702"),
        "smoking" to listOf("B700", "B701", "B702"),
        "smoker" to listOf("B700", "B701", "B702"),
        "cigarette" to listOf("B700", "B701", "B702"),
        "coffee" to listOf("B107", "B108"),
        "southern exposure" to listOf("B108"),
        "store" to listOf("B155"),
        "barber" to listOf("B155"),
        "haircut" to listOf("B155"),
        "post office" to listOf("B155"),
        "radio" to listOf("B155"),
        "highway 1" to listOf("B155"),
        "highway one" to listOf("B155"),
        "core" to listOf("B155"),
        "gym" to listOf("B075", "B078", "B155"),
        "fitness" to listOf("B075", "B078", "B155"),
        "workout" to listOf("B075", "B155"),
        "bowling" to listOf("B078"),
        "climbing" to listOf("B075"),
        "crary" to listOf("B001"),
        "science" to listOf("B001", "B004", "B144", "B146", "B160", "B162", "B164", "B170"),
        "aquarium" to listOf("B001"),
        "lab" to listOf("B001", "B144", "B146"),
        "library" to listOf("B001"),
        "medical" to listOf("B085", "B142", "B182"),
        "clinic" to listOf("B085"),
        "hospital" to listOf("B085"),
        "doctor" to listOf("B085"),
        "ambulance" to listOf("B085"),
        "hyperbaric" to listOf("B142"),
        "chamber" to listOf("B142"),
        "fire" to listOf("B140", "B141"),
        "firehouse" to listOf("B140", "B141"),
        "dorm" to listOf("B155", "B166", "B188", "B201", "B202", "B206", "B207", "B208", "B209", "B210", "B211", "B305"),
        "dorms" to listOf("B155", "B166", "B188", "B201", "B202", "B206", "B207", "B208", "B209", "B210", "B211", "B305"),
        "dormitory" to listOf("B155", "B166", "B188", "B201", "B202", "B206", "B207", "B208", "B209", "B210", "B211", "B305"),
        "housing" to listOf("B155", "B166", "B188", "B201", "B202", "B206", "B207", "B208", "B209", "B210", "B211", "B305"),
        "carpenter" to listOf("B306"),
        "wood shop" to listOf("B306"),
        "water" to listOf("B800"),
        "snoopy" to listOf("SNOOPY_POLE"),
        "webcam" to listOf("SNOOPY_POLE"),
        "discovery" to listOf("DISCOVERY_HUT"),
        "hut" to listOf("DISCOVERY_HUT"),
        "scott base" to listOf("SCOTT_BASE"),
        "scott" to listOf("SCOTT_BASE"),
        "kiwi" to listOf("SCOTT_BASE"),
        "new zealand" to listOf("SCOTT_BASE"),
        "nz" to listOf("SCOTT_BASE"),
        "turbine" to listOf("TURBINES"),
        "turbines" to listOf("TURBINES"),
        "wind" to listOf("TURBINES"),
        "balloon" to listOf("BALLOON_PAD"),
        "radome" to listOf("RADOME"),
        "satellite" to listOf("RADOME")
    )

    fun init(context: Context) {
        if (buildings.isNotEmpty()) return

        try {
            context.assets.open("assets/json/mcm-map-data.json").use { stream ->
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val jsonText = reader.readText()
                val array = JSONArray(jsonText)
                val list = mutableListOf<Building>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "").trim()
                    if (id.isEmpty()) continue

                    val name = obj.optString("name", "")
                    val photo = if (obj.has("photo") && !obj.isNull("photo")) obj.getString("photo") else null
                    val rawDesc = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null
                    val desc = cleanDescription(rawDesc)
                    val construction = if (obj.has("construction") && !obj.isNull("construction")) obj.getString("construction") else null
                    val occupancy = if (obj.has("occupancy") && !obj.isNull("occupancy")) obj.getString("occupancy") else null
                    val infoSheet = if (obj.has("info_sheet") && !obj.isNull("info_sheet")) obj.getString("info_sheet") else null

                    val aedsList = mutableListOf<String>()
                    val aedsArray = obj.optJSONArray("aeds")
                    if (aedsArray != null) {
                        for (j in 0 until aedsArray.length()) {
                            val item = aedsArray.optString(j, "").trim()
                            if (item.isNotEmpty()) aedsList.add(item)
                        }
                    }

                    val musterList = mutableListOf<String>()
                    val musterArray = obj.optJSONArray("muster_points")
                    if (musterArray != null) {
                        for (j in 0 until musterArray.length()) {
                            val item = musterArray.optString(j, "").trim()
                            if (item.isNotEmpty()) musterList.add(item)
                        }
                    }

                    val building = Building(
                        id = id,
                        name = name,
                        photo = photo,
                        description = desc,
                        construction = construction,
                        occupancy = occupancy,
                        aeds = aedsList,
                        musterPoints = musterList,
                        infoSheet = infoSheet
                    )
                    list.add(building)
                    buildingsById[id.uppercase()] = building
                    if (id.startsWith("B", ignoreCase = true)) {
                        buildingsById[id.substring(1)] = building
                    }
                }
                buildings = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanDescription(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val stripped = raw.replace(Regex("<[^>]*>"), "")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
            .replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
            .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (stripped.contains("No description is available for this building or structure", ignoreCase = true)) {
            return "No official description available for this facility."
        }
        return stripped.ifEmpty { null }
    }

    fun getAll(): List<Building> = buildings

    fun getById(id: String): Building? {
        val query = id.trim().uppercase()
        return buildingsById[query]
            ?: buildingsById[if (query.startsWith("B")) query.substring(1) else "B$query"]
            ?: buildings.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun search(query: String): List<Building> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val results = LinkedHashSet<Building>()

        // 1. Direct building ID exact/prefix match (e.g. "155" or "B155")
        getById(q)?.let { results.add(it) }

        // 2. Check alias dictionary
        for ((aliasKey, idList) in aliases) {
            if (aliasKey.contains(q) || q.contains(aliasKey)) {
                idList.forEach { id ->
                    getById(id)?.let { results.add(it) }
                }
            }
        }

        // 3. Match building ID, name, description, muster points, or AEDs
        buildings.forEach { bldg ->
            if (bldg.id.lowercase().contains(q) ||
                bldg.name.lowercase().contains(q) ||
                (bldg.description?.lowercase()?.contains(q) == true) ||
                bldg.musterPoints.any { it.lowercase().contains(q) } ||
                bldg.aeds.any { it.lowercase().contains(q) }
            ) {
                results.add(bldg)
            }
        }

        return results.take(15).toList()
    }

    // Category mapping: B221 REMOVED from dorms (Item 1)
    val categoryBuildings = mapOf(
        "aed" to emptyList(), // Populated dynamically below
        "dining" to listOf("B076", "B107", "B108", "B155"),
        "dorms" to listOf("B155", "B166", "B188", "B201", "B202", "B206", "B207", "B208", "B209", "B210", "B211", "B305"),
        "science" to listOf("B001", "B004", "B144", "B146", "B160", "B162", "B164", "B170"),
        "medical" to listOf("B085", "B142", "B182"),
        "fitness" to listOf("B075", "B078", "B155"),
        "smoking" to listOf("B700", "B701", "B702")
    )

    fun getCategoryIds(categoryKey: String): List<String> {
        return if (categoryKey == "aed") {
            buildings.filter { it.hasAeds }.map { it.id }
        } else {
            categoryBuildings[categoryKey] ?: emptyList()
        }
    }

    fun getSyncInfo(context: Context): SyncInfo {
        cachedSyncInfo?.let { return it }
        return try {
            context.assets.open("sync_metadata.json").use { stream ->
                val jsonStr = InputStreamReader(stream, "UTF-8").readText()
                val obj = JSONObject(jsonStr)
                val info = SyncInfo(
                    lastSyncedFormatted = obj.optString("last_synced_formatted", "Offline Packaged"),
                    sourceUrl = obj.optString("source_url", "https://www.mcmurdo.usap.gov/map/"),
                    totalPhotos = obj.optInt("total_photos", 0),
                    totalDocs = obj.optInt("total_documents", 0)
                )
                cachedSyncInfo = info
                info
            }
        } catch (e: Exception) {
            val fallback = SyncInfo()
            cachedSyncInfo = fallback
            fallback
        }
    }
}
