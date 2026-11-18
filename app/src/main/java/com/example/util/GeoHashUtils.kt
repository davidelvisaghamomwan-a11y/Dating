package com.example.util

import kotlin.math.min

data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLng: Double get() = (minLng + maxLng) / 2.0
}

object GeoHashUtils {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BASE32_INDEX = IntArray(128) { -1 }.apply {
        BASE32.forEachIndexed { index, c -> this[c.code] = index }
    }

    fun encode(latitude: Double, longitude: Double, precision: Int = 7): String {
        val clampedLat = latitude.coerceIn(-90.0, 90.0)
        val clampedLng = longitude.coerceIn(-180.0, 180.0)
        val targetLen = precision.coerceIn(1, 12)

        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        var isEvenBit = true
        var bit = 0
        var ch = 0
        val result = StringBuilder(targetLen)

        while (result.length < targetLen) {
            if (isEvenBit) {
                val mid = (lngMin + lngMax) / 2.0
                if (clampedLng >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2.0
                if (clampedLat >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }
            isEvenBit = !isEvenBit
            if (bit < 4) {
                bit++
            } else {
                result.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return result.toString()
    }

    fun decodeBounds(geohash: String): GeoBounds {
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0
        var isEvenBit = true

        for (c in geohash.lowercase()) {
            val idx = if (c.code < BASE32_INDEX.size) BASE32_INDEX[c.code] else -1
            require(idx >= 0) { "Invalid GeoHash character: $c" }
            for (bit in 4 downTo 0) {
                val mask = 1 shl bit
                if (isEvenBit) {
                    val mid = (lngMin + lngMax) / 2.0
                    if ((idx and mask) != 0) lngMin = mid else lngMax = mid
                } else {
                    val mid = (latMin + latMax) / 2.0
                    if ((idx and mask) != 0) latMin = mid else latMax = mid
                }
                isEvenBit = !isEvenBit
            }
        }
        return GeoBounds(minLat = latMin, maxLat = latMax, minLng = lngMin, maxLng = lngMax)
    }

    fun neighbor(geohash: String, dLatSteps: Int, dLngSteps: Int): String {
        if (geohash.isEmpty()) return ""
        val bounds = decodeBounds(geohash)
        val latHeight = bounds.maxLat - bounds.minLat
        val lngWidth = bounds.maxLng - bounds.minLng

        val targetLat = (bounds.centerLat + dLatSteps * latHeight).coerceIn(-89.9999, 89.9999)
        var targetLng = bounds.centerLng + dLngSteps * lngWidth
        if (targetLng > 180.0) targetLng -= 360.0
        if (targetLng < -180.0) targetLng += 360.0

        return encode(targetLat, targetLng, geohash.length)
    }

    /**
     * Returns the center GeoHash cell and its 8 adjacent GeoHash neighbors (up to 9 unique cells).
     */
    fun neighborhood(cellPrefix: String): List<String> {
        if (cellPrefix.isEmpty()) return emptyList()
        val cells = LinkedHashSet<String>(9)
        cells.add(cellPrefix)
        for (dLat in -1..1) {
            for (dLng in -1..1) {
                if (dLat != 0 || dLng != 0) {
                    val adj = neighbor(cellPrefix, dLat, dLng)
                    if (adj.isNotEmpty()) cells.add(adj)
                }
            }
        }
        return cells.toList()
    }

    /**
     * Selects optimal GeoHash cell precision for Firestore /geoCells/{cellId} bucket queries:
     * - <= 45 km: precision 4 (~39 km x 20 km per cell, 9-cell grid ~117 km x 60 km)
     * - > 45 km: precision 3 (~156 km x 156 km per cell, 9-cell grid ~468 km x 468 km)
     */
    fun precisionForRadiusKm(radiusKm: Double): Int {
        return if (radiusKm <= 45.0) 4 else 3
    }

    fun sharedPrefixLength(hashA: String, hashB: String): Int {
        val len = min(hashA.length, hashB.length)
        var count = 0
        for (i in 0 until len) {
            if (hashA[i] == hashB[i]) count++ else break
        }
        return count
    }
}
