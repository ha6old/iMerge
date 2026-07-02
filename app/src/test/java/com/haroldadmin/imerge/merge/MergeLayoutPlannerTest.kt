package com.haroldadmin.imerge.merge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeLayoutPlannerTest {
    @Test
    fun verticalMergeUsesWidestImageAndPreservesRatios() {
        val layout = MergeLayoutPlanner.plan(
            sources = listOf(ImageSize(1000, 500), ImageSize(500, 1000)),
            direction = MergeDirection.Vertical,
        )

        assertEquals(1000, layout.width)
        assertEquals(2500, layout.height)
        assertEquals(ImagePlacement(0, 0, 1000, 500), layout.placements[0])
        assertEquals(ImagePlacement(0, 500, 1000, 2000), layout.placements[1])
    }

    @Test
    fun horizontalMergeUsesTallestImageAndPreservesRatios() {
        val layout = MergeLayoutPlanner.plan(
            sources = listOf(ImageSize(1000, 500), ImageSize(500, 1000)),
            direction = MergeDirection.Horizontal,
        )

        assertEquals(2500, layout.width)
        assertEquals(1000, layout.height)
        assertEquals(ImagePlacement(0, 0, 2000, 1000), layout.placements[0])
        assertEquals(ImagePlacement(2000, 0, 500, 1000), layout.placements[1])
    }

    @Test
    fun oversizedOutputFitsPixelAndSideBudgets() {
        val layout = MergeLayoutPlanner.plan(
            sources = List(20) { ImageSize(8000, 6000) },
            direction = MergeDirection.Vertical,
            maxPixels = 10_000_000,
            maxSide = 8_000,
        )

        assertTrue(layout.width.toLong() * layout.height <= 10_000_000)
        assertTrue(layout.width <= 8_000)
        assertTrue(layout.height <= 8_000)
        assertEquals(20, layout.placements.size)
        layout.placements.zipWithNext().forEach { (first, second) ->
            assertEquals(first.y + first.height, second.y)
        }
    }
}
