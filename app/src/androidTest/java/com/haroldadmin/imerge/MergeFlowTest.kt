package com.haroldadmin.imerge

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class MergeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun twoPhotosPreviewAndExportInBothDirections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = createJpeg(context, "merge-test-first.jpg", 1200, 800, Color.rgb(231, 111, 81))
        val second = createJpeg(context, "merge-test-second.jpg", 600, 1200, Color.rgb(42, 157, 143))
        val createdOutputs = mutableListOf<Uri>()

        try {
            val initialId = latestMergedImage(context)?.id ?: -1L
            composeRule.activityRule.scenario.onActivity { activity ->
                ViewModelProvider(activity)[MergeViewModel::class.java]
                    .addPhotos(listOf(Uri.fromFile(first), Uri.fromFile(second)))
            }

            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("2 张照片").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("保存到相册").assertIsDisplayed().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText("拼接成功").fetchSemanticsNodes().isNotEmpty()
            }
            val vertical = waitForNewImage(context, initialId)
            createdOutputs += vertical.uri
            assertEquals(1200, vertical.width)
            assertEquals(3200, vertical.height)

            composeRule.onNodeWithText("保留原图").performClick()
            composeRule.onNodeWithText("横向").performClick()
            composeRule.onNodeWithText("保存到相册").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText("拼接成功").fetchSemanticsNodes().isNotEmpty()
            }
            val horizontal = waitForNewImage(context, vertical.id)
            createdOutputs += horizontal.uri
            assertEquals(2400, horizontal.width)
            assertEquals(1200, horizontal.height)
        } finally {
            createdOutputs.forEach { context.contentResolver.delete(it, null, null) }
            first.delete()
            second.delete()
        }
    }

    private fun waitForNewImage(context: Context, previousId: Long): MergedImage {
        var result: MergedImage? = null
        composeRule.waitUntil(20_000) {
            result = latestMergedImage(context)?.takeIf { it.id > previousId }
            result != null
        }
        return requireNotNull(result)
    }

    private fun latestMergedImage(context: Context): MergedImage? {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            ),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("iMerge_%"),
            "${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(0)
            return MergedImage(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                width = cursor.getInt(2),
                height = cursor.getInt(3),
            )
        }
        return null
    }

    private fun createJpeg(
        context: Context,
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private data class MergedImage(
        val id: Long,
        val uri: Uri,
        val width: Int,
        val height: Int,
    )
}
