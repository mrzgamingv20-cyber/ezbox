package com.mrzgaming.ezbox

data class Container(
    val id: String = "",
    var name: String = "Container",
    var desktopEnvironment: String = "xfce",
    var resolution: String = "960x540",
    val graphicsDriver: String = "auto",
    val createdAt: Long = 0L,
    val isActive: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "desktopEnvironment" to desktopEnvironment,
        "resolution" to resolution,
        "graphicsDriver" to graphicsDriver,
        "createdAt" to createdAt,
        "isActive" to isActive
    )

    companion object {
        fun fromMap(map: Map<String, *>): Container {
            return Container(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "Container",
                desktopEnvironment = map["desktopEnvironment"] as? String ?: "xfce",
                resolution = map["resolution"] as? String ?: "960x540",
                graphicsDriver = map["graphicsDriver"] as? String ?: "auto",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                isActive = map["isActive"] as? Boolean ?: false
            )
        }

        fun defaultContainer(): Container {
            return Container(
                id = "container_${System.currentTimeMillis()}",
                name = "Container ${(1..3).random()}",
                desktopEnvironment = "xfce",
                resolution = "960x540",
                graphicsDriver = "auto",
                createdAt = System.currentTimeMillis(),
                isActive = true
            )
        }
    }
}
