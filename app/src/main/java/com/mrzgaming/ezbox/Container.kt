package com.mrzgaming.ezbox

data class Container(
    val id: String = "",
    var name: String = "Container",
    val createdAt: Long = 0L,
    val isActive: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "createdAt" to createdAt,
        "isActive" to isActive
    )

    companion object {
        fun fromMap(map: Map<String, *>): Container {
            return Container(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "Container",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                isActive = map["isActive"] as? Boolean ?: false
            )
        }

        fun defaultContainer(): Container {
            return Container(
                id = "container_${System.currentTimeMillis()}",
                name = "Container ${(1..3).random()}",
                createdAt = System.currentTimeMillis(),
                isActive = true
            )
        }
    }
}
