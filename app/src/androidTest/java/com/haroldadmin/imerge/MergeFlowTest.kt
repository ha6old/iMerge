package com.haroldadmin.imerge

import android.Manifest
import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.haroldadmin.imerge.gallery.GalleryPhoto
import com.haroldadmin.imerge.merge.MergeDirection
import com.haroldadmin.imerge.ui.galleryPhotoTestTag
import com.haroldadmin.imerge.ui.photoViewerTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@LargeTest
class MergeFlowTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun twoPhotosPreviewAndExportVertically() {
        twoPhotosPreviewAndExport(
            direction = MergeDirection.Vertical,
            expectedWidth = 1200,
            expectedHeight = 3200,
        )
    }

    @Test
    fun twoPhotosPreviewAndExportHorizontally() {
        twoPhotosPreviewAndExport(
            direction = MergeDirection.Horizontal,
            expectedWidth = 2400,
            expectedHeight = 1200,
        )
    }

    @Test
    fun photoBrowserOpensAndSwipesBetweenPhotos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.currentTimeMillis()
        val first = createMediaStoreJpeg(context, "browser-test-first-$suffix.jpg", 700, 900, Color.BLUE)
        val second = createMediaStoreJpeg(context, "browser-test-second-$suffix.jpg", 900, 700, Color.GREEN)

        try {
            waitForGalleryToContain(first, second)

            lateinit var start: GalleryPhoto
            lateinit var next: GalleryPhoto
            composeRule.activityRule.scenario.onActivity { activity ->
                val gallery = ViewModelProvider(activity)[MergeViewModel::class.java].state.value.gallery
                val firstIndex = gallery.indexOfFirst { it.key == first.key }
                val secondIndex = gallery.indexOfFirst { it.key == second.key }
                val startIndex = minOf(firstIndex, secondIndex)
                start = gallery[startIndex]
                next = gallery[startIndex + 1]
            }

            composeRule.onNodeWithTag(galleryPhotoTestTag(start.key)).performClick()
            composeRule.onNodeWithTag(photoViewerTestTag(start.key)).assertIsDisplayed()
            composeRule.onNodeWithTag(photoViewerTestTag(start.key)).performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(photoViewerTestTag(next.key)).assertIsDisplayed()
        } finally {
            context.contentResolver.delete(first.uri, null, null)
            context.contentResolver.delete(second.uri, null, null)
        }
    }

    private fun twoPhotosPreviewAndExport(
        direction: MergeDirection,
        expectedWidth: Int,
        expectedHeight: Int,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.currentTimeMillis()
        val first = createMediaStoreJpeg(context, "merge-test-first-$suffix.jpg", 1200, 800, Color.rgb(231, 111, 81))
        val second = createMediaStoreJpeg(context, "merge-test-second-$suffix.jpg", 600, 1200, Color.rgb(42, 157, 143))
        val createdOutputs = mutableListOf<Uri>()
        val saveLabel = string(R.string.save_to_gallery)
        val successTitle = string(R.string.merge_success_title)
        val keepLabel = string(R.string.keep_originals)

        try {
            val initialId = latestMergedImage(context)?.id ?: -1L
            waitForGalleryToContain(first, second)
            selectAndOpenMerge(first, second)

            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText(saveLabel).fetchSemanticsNodes().isNotEmpty()
            }
            if (direction == MergeDirection.Horizontal) {
                composeRule.onNodeWithText(string(R.string.direction_horizontal)).performClick()
            }
            composeRule.onNodeWithText(saveLabel).assertIsDisplayed().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText(successTitle).fetchSemanticsNodes().isNotEmpty()
            }
            val output = waitForNewImage(context, initialId)
            createdOutputs += output.uri
            assertEquals(expectedWidth, output.width)
            assertEquals(expectedHeight, output.height)
            composeRule.onNodeWithText(keepLabel).performClick()
        } finally {
            createdOutputs.forEach { context.contentResolver.delete(it, null, null) }
            context.contentResolver.delete(first.uri, null, null)
            context.contentResolver.delete(second.uri, null, null)
        }
    }

    private fun string(id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)

    private fun waitForGalleryToContain(vararg photos: GalleryPhoto) {
        composeRule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[MergeViewModel::class.java].onPhotoAccessGranted()
        }
        composeRule.waitUntil(10_000) {
            var loaded = false
            composeRule.activityRule.scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[MergeViewModel::class.java]
                val galleryKeys = viewModel.state.value.gallery.mapTo(mutableSetOf()) { it.key }
                loaded = photos.all { it.key in galleryKeys }
            }
            loaded
        }
    }

    private fun selectAndOpenMerge(first: GalleryPhoto, second: GalleryPhoto) {
        composeRule.onNodeWithText(string(R.string.select_photos)).performClick()
        composeRule.onNodeWithTag(galleryPhotoTestTag(first.key)).performClick()
        composeRule.onNodeWithTag(galleryPhotoTestTag(second.key)).performClick()
        composeRule
            .onNodeWithContentDescription(string(R.string.merge_selected_photos))
            .assertIsDisplayed()
            .performClick()
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

    private fun createMediaStoreJpeg(
        context: Context,
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): GalleryPhoto {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/iMergeTest")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        ) ?: throw IOException("Could not create test image in MediaStore")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Could not encode test image")
                }
            } ?: throw IOException("Could not open test image output stream")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            val externalUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                ContentUris.parseId(uri),
            )
            return GalleryPhoto(externalUri, width, height)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private data class MergedImage(
        val id: Long,
        val uri: Uri,
        val width: Int,
        val height: Int,
    )
}
