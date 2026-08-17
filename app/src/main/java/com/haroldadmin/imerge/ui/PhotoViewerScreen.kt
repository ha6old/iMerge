package com.haroldadmin.imerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.haroldadmin.imerge.R
import com.haroldadmin.imerge.gallery.GalleryPhoto

@Composable
fun PhotoViewerScreen(
    photos: List<GalleryPhoto>,
    initialPhotoKey: String?,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) return

    val initialPage = photos.indexOfFirst { it.key == initialPhotoKey }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> photos[page].key },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            AsyncImage(
                model = photo.uri,
                contentDescription = stringResource(
                    R.string.photo_viewer_photo,
                    page + 1,
                    photos.size,
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(photoViewerTestTag(photo.key)),
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = .58f),
        ) {
            Text(
                text = stringResource(
                    R.string.photo_viewer_position,
                    pagerState.currentPage + 1,
                    photos.size,
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

internal fun photoViewerTestTag(key: String) = "photo-viewer-$key"
