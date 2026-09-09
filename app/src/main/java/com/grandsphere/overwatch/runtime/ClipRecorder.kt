package com.grandsphere.overwatch.runtime

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.grandsphere.overwatch.domain.model.RecordCameraMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Loops N-second clips to MediaStore while Alarm Mode is active.
 * Camera stays open across clips; each file is finalized with stop() before the fd is closed.
 */
@Suppress("DEPRECATION")
class ClipRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private val lanes = mutableListOf<Lane>()

    fun start(video: Boolean, audio: Boolean, mode: RecordCameraMode, clipMs: Long) {
        stop()
        val duration = clipMs.coerceIn(3_000L, 120_000L)
        job = scope.launch(Dispatchers.IO) {
            try {
                when (mode) {
                    RecordCameraMode.FRONT -> startSingle(video, audio, front = true, duration)
                    RecordCameraMode.BACK -> startSingle(video, audio, front = false, duration)
                    RecordCameraMode.BOTH -> startBoth(video, audio, duration)
                }
            } finally {
                releaseAll()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        releaseAll()
    }

    private suspend fun startSingle(video: Boolean, audio: Boolean, front: Boolean, duration: Long) {
        if (video) {
            val cam = openCamera(front)
            if (cam == null) {
                VerboseLog.fail("Record", "camera open failed front=$front")
                return
            }
            lanes += Lane(camera = cam, tag = if (front) "front" else "back")
        } else {
            lanes += Lane(tag = "audio")
        }
        VerboseLog.ok("Record", "start video=$video audio=$audio front=$front clipMs=$duration")
        loopLane(lanes.first(), video, audio, duration)
    }

    private suspend fun startBoth(video: Boolean, audio: Boolean, duration: Long) {
        if (!video) {
            startSingle(video = false, audio = audio, front = true, duration = duration)
            return
        }
        val frontCam = openCamera(front = true)
        val backCam = openCamera(front = false)
        if (frontCam != null && backCam != null) {
            val frontLane = Lane(camera = frontCam, tag = "front")
            val backLane = Lane(camera = backCam, tag = "back")
            lanes += frontLane
            lanes += backLane
            VerboseLog.ok("Record", "start BOTH dual clipMs=$duration")
            coroutineScope {
                launch { loopLane(frontLane, video = true, audio = audio, clipMs = duration) }
                // Back is video-only to avoid mic conflicts.
                launch { loopLane(backLane, video = true, audio = false, clipMs = duration) }
            }
            return
        }
        runCatching { backCam?.release() }
        runCatching { frontCam?.release() }
        VerboseLog.d("Record", "BOTH unavailable, falling back to front")
        startSingle(video = true, audio = audio, front = true, duration = duration)
    }

    private suspend fun loopLane(lane: Lane, video: Boolean, audio: Boolean, clipMs: Long) {
        var n = 0
        while (currentCoroutineContext().isActive) {
            n += 1
            val ok = runCatching { recordOne(lane, video, audio, clipMs, n) }.getOrDefault(false)
            if (!ok) VerboseLog.fail("Record", "clip ${lane.tag}-$n failed")
            if (!currentCoroutineContext().isActive) break
            if (!ok) delay(500)
        }
    }

    private suspend fun recordOne(
        lane: Lane,
        video: Boolean,
        audio: Boolean,
        clipMs: Long,
        index: Int,
    ): Boolean {
        if (video && lane.camera == null) return false
        val mime = if (video) "video/mp4" else "audio/mp4"
        val name = "overwatch-${lane.tag}-${System.currentTimeMillis()}-$index.mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (video) "${Environment.DIRECTORY_MOVIES}/Overwatch"
                    else "${Environment.DIRECTORY_MUSIC}/Overwatch",
                )
            }
        }
        val collection = if (video) {
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        }
        val uri = context.contentResolver.insert(collection, values) ?: return false
        val pfd = context.contentResolver.openFileDescriptor(uri, "w")
        if (pfd == null) {
            deleteUri(uri)
            return false
        }
        var started = false
        try {
            val rec = MediaRecorder()
            lane.recorder = rec
            if (video) {
                val cam = lane.camera ?: run {
                    deleteUri(uri)
                    return false
                }
                if (lane.preview == null) {
                    val tex = SurfaceTexture(10 + lanes.indexOf(lane))
                    lane.preview = tex
                    cam.setPreviewTexture(tex)
                    cam.startPreview()
                }
                cam.unlock()
                rec.setCamera(cam)
                if (audio) rec.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                rec.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            } else {
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setOutputFile(pfd.fileDescriptor)
            if (video) {
                rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                rec.setVideoSize(1280, 720)
                rec.setVideoEncodingBitRate(2_000_000)
                rec.setVideoFrameRate(24)
            }
            if (audio || !video) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            rec.setMaxDuration(clipMs.toInt())
            rec.prepare()
            val ended = kotlinx.coroutines.CompletableDeferred<Unit>()
            rec.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    ended.complete(Unit)
                }
            }
            rec.setOnErrorListener { _, _, _ -> ended.complete(Unit) }
            rec.start()
            started = true
            withTimeoutOrNull(clipMs + 1_500L) { ended.await() }
        } catch (_: Exception) {
            if (!started) deleteUri(uri)
            return false
        } finally {
            stopRecorder(lane)
            pfd.close()
            if (video) runCatching { lane.camera?.lock() }
        }
        return started
    }

    private fun openCamera(front: Boolean): Camera? {
        val wanted = if (front) Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
        val info = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            if (info.facing == wanted) {
                return runCatching { Camera.open(i) }.getOrNull()
            }
        }
        return if (front) runCatching { Camera.open() }.getOrNull() else null
    }

    private fun stopRecorder(lane: Lane) {
        val rec = lane.recorder
        lane.recorder = null
        if (rec == null) return
        runCatching { rec.stop() }
        runCatching { rec.reset() }
        runCatching { rec.release() }
    }

    private fun releaseAll() {
        lanes.forEach { lane ->
            stopRecorder(lane)
            runCatching { lane.camera?.reconnect() }
            runCatching { lane.camera?.release() }
            lane.camera = null
            lane.preview?.release()
            lane.preview = null
        }
        lanes.clear()
    }

    private fun deleteUri(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private class Lane(
        var camera: Camera? = null,
        var recorder: MediaRecorder? = null,
        var preview: SurfaceTexture? = null,
        val tag: String,
    )
}
