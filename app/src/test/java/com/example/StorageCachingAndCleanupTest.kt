package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.CloudMediaStorageService
import com.example.util.PreparedVideoUpload
import com.example.util.VideoClipHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageCachingAndCleanupTest {

    @Test
    fun buildMediaMetadata_setsCacheControlToOneYearForImagesAndVideos() {
        assertEquals("public, max-age=31536000", CloudMediaStorageService.DEFAULT_MEDIA_CACHE_CONTROL)

        val imageMeta = CloudMediaStorageService.buildMediaMetadata("image/jpeg")
        assertEquals("image/jpeg", imageMeta.contentType)
        assertEquals("public, max-age=31536000", imageMeta.cacheControl)

        val videoMeta = CloudMediaStorageService.buildMediaMetadata("video/mp4")
        assertEquals("video/mp4", videoMeta.contentType)
        assertEquals("public, max-age=31536000", videoMeta.cacheControl)

        val fallbackMeta = CloudMediaStorageService.buildMediaMetadata(contentType = "   ", cacheControl = "")
        assertEquals("application/octet-stream", fallbackMeta.contentType)
        assertEquals("public, max-age=31536000", fallbackMeta.cacheControl)
    }

    @Test
    fun cleanupPreparedUpload_deletesTemporaryCompressedVideoFileSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = File(context.cacheDir, "chat_videos").apply { mkdirs() }
        val stagedFile = File(cacheDir, "video_upload_test_1.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        assertTrue(stagedFile.exists())

        val prepared = PreparedVideoUpload(
            inlineDataUrl = null,
            chunkBase64List = emptyList(),
            thumbnailDataUrl = null,
            mimeType = "video/mp4",
            durationSec = 3L,
            sizeBytes = 4L,
            localCachedFile = stagedFile
        )

        VideoClipHelper.cleanupPreparedUpload(prepared)
        assertFalse(stagedFile.exists())

        // Calling cleanup again or with null should not throw
        VideoClipHelper.cleanupPreparedUpload(prepared)
        VideoClipHelper.cleanupPreparedUpload(null)
    }

    @Test
    fun uploadFileWithMeta_deletesTempFileWhenUploadFailsAndDeleteFlagEnabled() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File.createTempFile("upload_fail_cleanup_", ".mp4", context.cacheDir).apply {
            writeBytes(byteArrayOf(10, 20, 30, 40))
        }
        assertTrue(tempFile.exists())

        // authProvider throws/returns unauthenticated error -> upload fails in validatePrerequisites
        val service = CloudMediaStorageService(
            authProvider = { throw IllegalStateException("Simulated auth unavailability in test") }
        )

        val result = service.uploadFileWithMeta(
            file = tempFile,
            remotePath = "users/test_user/feed_posts/test.mp4",
            contentType = "video/mp4",
            deleteLocalFileAfterUpload = true
        )

        assertTrue(result.isFailure)
        assertFalse("Temp upload file should be deleted when upload fails", tempFile.exists())
    }

    @Test
    fun prepareVideoForUpload_cleansUpStagingFileWhenSourceUriCannotBeRead() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheKey = "failed_prep_test"
        val expectedStagingFile = VideoClipHelper.cacheTargetFile(context, cacheKey)
        expectedStagingFile.delete()

        val emptySource = File.createTempFile("empty_src_", ".mp4", context.cacheDir)
        val result = VideoClipHelper.prepareVideoForUpload(
            context = context,
            uri = Uri.fromFile(emptySource),
            cacheKey = cacheKey
        )

        assertTrue(result.isFailure)
        assertFalse("Partial staging file should not remain after failed prep", expectedStagingFile.exists())
        emptySource.delete()
    }
}
