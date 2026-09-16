package com.example.live.ui

import kotlin.math.round

/** 1234 -> "1.2K", 135300 -> "135.3K", 2100000 -> "2.1M". */
fun formatLiveCount(value: Int): String = formatLiveCount(value.toLong())

fun formatLiveCount(value: Long): String = when {
    value >= 1_000_000 -> compact(value / 1_000_000.0) + "M"
    value >= 1_000 -> compact(value / 1_000.0) + "K"
    else -> value.toString()
}

private fun compact(value: Double): String {
    val rounded = round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

fun formatLiveElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
