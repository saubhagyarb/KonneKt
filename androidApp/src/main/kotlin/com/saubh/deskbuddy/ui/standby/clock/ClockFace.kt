package com.saubh.deskbuddy.ui.standby.clock

import kotlinx.coroutines.flow.Flow

enum class ClockFace {
    FLIP, BUBBLE, NEON, DOT_MATRIX, SOLAR, ANALOG;

    companion object {
        fun fromName(name: String?): ClockFace = entries.firstOrNull { it.name == name } ?: FLIP
    }
}

interface ClockFaceStore {
    val face: Flow<ClockFace>
    suspend fun save(face: ClockFace)
}
