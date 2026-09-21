package com.aimforge.app.domain

/** Text helpers. They only reformat stored values; they never invent any. */
object SessionFormat {
    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Only values the user actually entered. */
    fun sensitivity(camera: Int?, ads: Int?, gyro: Int?, adsGyro: Int?): String {
        val parts = buildList {
            camera?.let { add("Camera $it") }
            ads?.let { add("ADS $it") }
            gyro?.let { add("Gyro $it") }
            adsGyro?.let { add("ADS Gyro $it") }
        }
        return if (parts.isEmpty()) "Not entered" else parts.joinToString("  ")
    }

    fun orUnknown(value: String?): String = value ?: "Unknown"
    fun orUnknown(value: Int?): String = value?.toString() ?: "Unknown"
}
