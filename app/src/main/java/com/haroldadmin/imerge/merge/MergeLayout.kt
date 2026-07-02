package com.haroldadmin.imerge.merge

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MergeDirection { Vertical, Horizontal }

data class ImageSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0)
    }
}

data class ImagePlacement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class MergeLayout(
    val width: Int,
    val height: Int,
    val placements: List<ImagePlacement>,
)

object MergeLayoutPlanner {
    const val DEFAULT_MAX_PIXELS = 24_000_000L
    const val DEFAULT_MAX_SIDE = 16_000

    fun plan(
        sources: List<ImageSize>,
        direction: MergeDirection,
        maxPixels: Long = DEFAULT_MAX_PIXELS,
        maxSide: Int = DEFAULT_MAX_SIDE,
    ): MergeLayout {
        require(sources.isNotEmpty())
        require(maxPixels > 0 && maxSide > 0)

        val natural = when (direction) {
            MergeDirection.Vertical -> {
                val width = sources.maxOf { it.width }.toDouble()
                val heights = sources.map { width * it.height / it.width }
                NaturalLayout(width, heights.sum(), heights)
            }

            MergeDirection.Horizontal -> {
                val height = sources.maxOf { it.height }.toDouble()
                val widths = sources.map { height * it.width / it.height }
                NaturalLayout(widths.sum(), height, widths)
            }
        }

        val pixelScale = sqrt(maxPixels / (natural.width * natural.height))
        val sideScale = min(maxSide / natural.width, maxSide / natural.height)
        val scale = min(1.0, min(pixelScale, sideScale)).coerceAtLeast(0.0001)

        val placements = mutableListOf<ImagePlacement>()
        var cursor = 0
        sources.indices.forEach { index ->
            when (direction) {
                MergeDirection.Vertical -> {
                    val width = (natural.width * scale).roundToInt().coerceAtLeast(1)
                    val end = if (index == sources.lastIndex) {
                        (natural.height * scale).roundToInt()
                    } else {
                        cursor + (natural.segments[index] * scale).roundToInt().coerceAtLeast(1)
                    }
                    placements += ImagePlacement(0, cursor, width, (end - cursor).coerceAtLeast(1))
                    cursor = end
                }

                MergeDirection.Horizontal -> {
                    val height = (natural.height * scale).roundToInt().coerceAtLeast(1)
                    val end = if (index == sources.lastIndex) {
                        (natural.width * scale).roundToInt()
                    } else {
                        cursor + (natural.segments[index] * scale).roundToInt().coerceAtLeast(1)
                    }
                    placements += ImagePlacement(cursor, 0, (end - cursor).coerceAtLeast(1), height)
                    cursor = end
                }
            }
        }

        return when (direction) {
            MergeDirection.Vertical -> MergeLayout(placements.first().width, cursor, placements)
            MergeDirection.Horizontal -> MergeLayout(cursor, placements.first().height, placements)
        }
    }

    private data class NaturalLayout(
        val width: Double,
        val height: Double,
        val segments: List<Double>,
    )
}
