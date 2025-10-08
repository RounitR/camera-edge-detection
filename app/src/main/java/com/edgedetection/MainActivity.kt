package com.edgedetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
// import android.view.TextureView // removed
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 200
        private var isNativeLibraryLoaded = false
        
        // Native methods for frame processing
        external fun stringFromJNI(): String
        external fun processFrameNative(frameData: ByteArray, width: Int, height: Int, rowStride: Int, pixelStride: Int)
        external fun processFrameAndReturn(frameData: ByteArray, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray?
        external fun initializeOpenCV(): Boolean
        external fun setCannyThresholds(low: Double, high: Double)
        
        fun loadNativeLibrary(): Boolean {
            if (!isNativeLibraryLoaded) {
                try {
                    // Explicitly load C++ runtime and OpenCV before our native library to avoid dlopen symbol issues
                    System.loadLibrary("c++_shared")
                    android.util.Log.d("MainActivity", "libc++_shared loaded successfully")
                    System.loadLibrary("opencv_java4")
                    android.util.Log.d("MainActivity", "libopencv_java4 loaded successfully")
                    System.loadLibrary("edgedetection")
                    android.util.Log.d("MainActivity", "Native library loaded successfully")
                    isNativeLibraryLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    android.util.Log.e("MainActivity", "Failed to load native libraries: ${e.message}")
                    return false
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Unexpected error loading native libraries: ${e.message}")
                    return false
                }
            }
            return true
        }
    }

    // Removed TextureView; we render via GLSurfaceView only
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var toggleButton: Button
    private lateinit var edgeRenderer: EdgeRenderer
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isEdgeDetectionEnabled = false
    private var activeCameraId: String? = null
    private lateinit var fpsTextView: TextView
    private var uiHandler: Handler? = null
    // HTTP frame server
    private var frameServer: FrameServer? = null
    
    // Frame capture components
    private var imageReader: ImageReader? = null
    private val frameWidth = 1280
    private val frameHeight = 720
    
    // Processing thread components
    private var processingThread: HandlerThread? = null
    private var processingHandler: Handler? = null
    private val frameQueue: BlockingQueue<FrameData> = LinkedBlockingQueue(1) // Limit queue size to 1 for stronger backpressure
    
    // Performance monitoring
    private val frameCount = AtomicLong(0)
    private val processedFrameCount = AtomicLong(0)
    private var lastFpsTime = System.currentTimeMillis()
    private var lastProcessTime = System.currentTimeMillis()
    private val targetFps = 15.0 // Target ~15 FPS for processing only to stabilize under load
    private val minFrameInterval = (1000.0 / targetFps).toLong() // ~66ms between processed frames when targetFps=15
    
    // Frame data class for queue
    data class FrameData(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val rowStride: Int,
        val pixelStride: Int,
        val timestamp: Long
    )
    
    // OpenCV Manager callback
    // OpenCV is initialized via OpenCVLoader.initDebug() in onResume()
    // Native library is loaded via loadNativeLibrary()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // GLSurfaceView is the only visible surface; no TextureView
        glSurfaceView = findViewById(R.id.glSurfaceView)
        toggleButton = findViewById(R.id.toggleButton)
        val lowSeek: SeekBar = findViewById(R.id.lowThresholdSeekBar)
        val highSeek: SeekBar = findViewById(R.id.highThresholdSeekBar)
        fpsTextView = findViewById(R.id.fpsText)

        // Initialize OpenGL renderer
        edgeRenderer = EdgeRenderer()
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(edgeRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        edgeRenderer.setGLSurfaceView(glSurfaceView)

        // Initial state: edge detection ON, GLSurfaceView visible
        isEdgeDetectionEnabled = true
        edgeRenderer.setShowProcessedFrame(true)
        toggleButton.text = "Disable Edge Detection"

        toggleButton.setOnClickListener {
            isEdgeDetectionEnabled = !isEdgeDetectionEnabled
            toggleButton.text = if (isEdgeDetectionEnabled) "Disable Edge Detection" else "Enable Edge Detection"
            edgeRenderer.setShowProcessedFrame(isEdgeDetectionEnabled)
        }

        // Threshold SeekBars -> native thresholds
        lowSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                findViewById<android.widget.TextView>(R.id.lowThresholdLabel).text = "Low Threshold: $progress"
                safeSetCannyThresholds(progress.toDouble(), highSeek.progress.toDouble())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        highSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                findViewById<android.widget.TextView>(R.id.highThresholdLabel).text = "High Threshold: $progress"
                safeSetCannyThresholds(lowSeek.progress.toDouble(), progress.toDouble())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup ImageReader and processing
        setupImageReader()
        startProcessingThread()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        loadNativeLibrary()
        if (OpenCVLoader.initDebug()) {
            android.util.Log.d("MainActivity", "OpenCV loaded via initDebug")
        } else {
            android.util.Log.e("MainActivity", "Failed to load OpenCV via initDebug")
        }
        val ok = try { initializeOpenCV() } catch (e: Throwable) { false }
        if (!ok) {
            Toast.makeText(this, "Failed to initialize edge detection.", Toast.LENGTH_LONG).show()
        }
        // No TextureView. Open camera immediately.
        openCamera()
        glSurfaceView.onResume()
        safeSetCannyThresholds(findViewById<SeekBar>(R.id.lowThresholdSeekBar).progress.toDouble(),
            findViewById<SeekBar>(R.id.highThresholdSeekBar).progress.toDouble())
        // Start FPS overlay updates
        uiHandler = Handler(mainLooper)
        uiHandler?.post(fpsUpdateRunnable)
        // Start HTTP frame server
        startFrameServer()
    }

    override fun onPause() {
        glSurfaceView.onPause()
        closeCamera()
        stopBackgroundThread()
        stopProcessingThread()
        // Stop HTTP frame server
        stopFrameServer()
        super.onPause()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            return
        }

        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            activeCameraId = cameraId
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
            finish()
        }
    }

    private fun setupImageReader() {
        // Reduce buffer count to 1 to avoid lag from queued images; rely on acquireLatestImage
        imageReader = ImageReader.newInstance(frameWidth, frameHeight, ImageFormat.YUV_420_888, 1)
        imageReader?.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
    }
    
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        image?.let {
            processFrame(it)
            it.close()
        }
    }
    
    private fun processFrame(image: Image) {
        // Always enqueue/update original frame to renderer at full rate; throttle only processed frames
        val currentTime = System.currentTimeMillis()
        frameCount.incrementAndGet()
        try {
            val planes = image.planes
            val yPlane = planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)
            // Update original frame immediately for smooth preview
            edgeRenderer.updateOriginalFrame(
                yArray,
                image.width,
                image.height,
                yPlane.rowStride
            )
            // Clear pending processed to avoid showing stale frames when toggling rapidly
            if (!isEdgeDetectionEnabled) {
                // If disabled, do not enqueue processed frames
            } else if (currentTime - lastProcessTime >= minFrameInterval) {
                val frameData = FrameData(
                    yArray,
                    image.width,
                    image.height,
                    yPlane.rowStride,
                    yPlane.pixelStride,
                    currentTime
                )
                frameQueue.poll() // drop any queued older frame to minimize latency
                if (!frameQueue.offer(frameData)) {
                    android.util.Log.d("MainActivity", "Frame queue full, dropping processed frame")
                }
                lastProcessTime = currentTime
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error processing frame: ${e.message}")
        }
    }

    private fun createCameraPreview() {
        try {
            val imageReaderSurface = imageReader?.surface
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            imageReaderSurface?.let { captureRequestBuilder?.addTarget(it) }

            val surfaces = mutableListOf<Surface>()
            imageReaderSurface?.let { surfaces.add(it) }

            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        updatePreview(captureRequestBuilder)
                        // Update renderer orientation once the session is configured
                        updateRendererOrientation()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(this@MainActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun updateRendererOrientation() {
        try {
            val camId = activeCameraId ?: return
            android.util.Log.d("MainActivity", "updateRendererOrientation: Starting with camId=$camId")
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val characteristics = manager.getCameraCharacteristics(camId)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val displayRotation = windowManager.defaultDisplay.rotation
            android.util.Log.d("MainActivity", "updateRendererOrientation: sensorOrientation=$sensorOrientation, lensFacing=$lensFacing, displayRotation=$displayRotation")
            val deviceRotationDegrees = when (displayRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val totalRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + deviceRotationDegrees) % 360
            } else {
                // Back camera: use standard calculation without mirroring
                (sensorOrientation - deviceRotationDegrees + 360) % 360
            }
            android.util.Log.d("MainActivity", "Orientation update: camId=$camId, sensorOrientation=$sensorOrientation, lensFacing=$lensFacing, deviceRotationDegrees=$deviceRotationDegrees, totalRotation=$totalRotation")
            edgeRenderer.setRotationDegrees(totalRotation)
            // Mirror horizontally only for front camera (selfie view)
            val mirror = when (lensFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> true
                CameraCharacteristics.LENS_FACING_BACK -> false
                else -> false
            }
            android.util.Log.d("MainActivity", "updateRendererOrientation: Setting mirror=$mirror for lensFacing=$lensFacing")
            edgeRenderer.setMirrorX(mirror)
            // Also control vertical mirroring to correct upside-down previews for back camera
            val mirrorY = when (lensFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> false
                CameraCharacteristics.LENS_FACING_BACK -> true
                else -> false
            }
            android.util.Log.d("MainActivity", "updateRendererOrientation: Setting mirrorY=$mirrorY for lensFacing=$lensFacing")
            edgeRenderer.setMirrorY(mirrorY)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to update renderer orientation: ${e.message}")
        }
    }

    private fun updatePreview(captureRequestBuilder: CaptureRequest.Builder?) {
        if (cameraDevice == null || captureRequestBuilder == null) return
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    private fun startProcessingThread() {
        processingThread = HandlerThread("Frame Processing")
        processingThread?.start()
        processingHandler = Handler(processingThread?.looper!!)
        
        // Start the frame processing loop
        processingHandler?.post(frameProcessingRunnable)
    }
    
    private fun stopProcessingThread() {
        frameQueue.clear()
        processingThread?.quitSafely()
        try {
            processingThread?.join()
            processingThread = null
            processingHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
    
    private val frameProcessingRunnable = object : Runnable {
        override fun run() {
            try {
                val frameData = frameQueue.take()
                if (isEdgeDetectionEnabled) {
                    try {
                        val nativeResult = processFrameAndReturn(
                            frameData.data,
                            frameData.width,
                            frameData.height,
                            frameData.rowStride,
                            frameData.pixelStride
                        )
                        if (nativeResult != null) {
                            edgeRenderer.updateProcessedFrame(nativeResult, frameData.width, frameData.height)
                            // Publish JPEG to HTTP server
                            val jpeg = grayscaleToJpeg(nativeResult, frameData.width, frameData.height)
                            frameServer?.updateFrameJpeg(jpeg)
                            frameServer?.updateStatus("running")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Native processing error: ${e.message}")
                        frameServer?.updateStatus("error: ${e.message}")
                    }
                }
                // Throttle re-posting to approximate target processed FPS under load
                val delayMs = minFrameInterval
                processingHandler?.postDelayed(this, delayMs)
                processedFrameCount.incrementAndGet()
            } catch (e: InterruptedException) {
                android.util.Log.d("MainActivity", "Processing thread interrupted")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in processing thread: ${e.message}")
                // Reschedule with delay to avoid tight loop on errors
                processingHandler?.postDelayed(this, minFrameInterval)
            }
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera Background")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * A native method that is implemented by the 'edgedetection' native library,
     * which is packaged with this app.
     */
    private val fpsUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                val elapsed = now - lastFpsTime
                if (elapsed >= 1000) {
                    val camFps = frameCount.get().toDouble() * 1000.0 / elapsed.toDouble()
                    val procFps = processedFrameCount.get().toDouble() * 1000.0 / elapsed.toDouble()
                    fpsTextView.text = String.format("Cam FPS: %.1f | Proc FPS: %.1f", camFps, procFps)
                    frameCount.set(0)
                    processedFrameCount.set(0)
                    lastFpsTime = now
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "FPS overlay update error: ${e.message}")
            } finally {
                uiHandler?.postDelayed(this, 500)
            }
        }
    }

    private fun safeSetCannyThresholds(low: Double, high: Double) {
        try {
            setCannyThresholds(low, high)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "setCannyThresholds error: ${t.message}")
        }
    }

    private fun grayscaleToJpeg(gray: ByteArray, width: Int, height: Int, quality: Int = 70): ByteArray? {
    return try {
        val rgbaSize = width * height * 4
        val rgba = ByteArray(rgbaSize)
        var dst = 0
        for (i in gray.indices) {
            val g = gray[i].toInt() and 0xFF
            val b = g.toByte()
            rgba[dst] = b
            rgba[dst + 1] = b
            rgba[dst + 2] = b
            rgba[dst + 3] = 0xFF.toByte()
            dst += 4
        }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = java.nio.ByteBuffer.wrap(rgba)
        bmp.copyPixelsFromBuffer(buffer)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bmp.recycle()
        baos.toByteArray()
    } catch (t: Throwable) {
        android.util.Log.e("MainActivity", "grayscaleToJpeg error: ${t.message}")
        null
    }
}

private fun startFrameServer() {
    try {
        if (frameServer == null) {
            frameServer = FrameServer(8081)
            frameServer?.onSettings = { low, high, enabled ->
                runOnUiThread {
                    try {
                        isEdgeDetectionEnabled = enabled
                        edgeRenderer.setShowProcessedFrame(enabled)
                        safeSetCannyThresholds(low.toDouble(), high.toDouble())
                        // Update UI labels and toggle button text
                        findViewById<android.widget.TextView>(R.id.lowThresholdLabel).text = "Low Threshold: $low"
                        findViewById<android.widget.TextView>(R.id.highThresholdLabel).text = "High Threshold: $high"
                        toggleButton.text = if (enabled) "Disable Edge Detection" else "Enable Edge Detection"
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "apply settings error: ${e.message}")
                    }
                }
            }
            frameServer?.start()
            android.util.Log.i("MainActivity", "FrameServer started on port 8081")
        }
    } catch (t: Throwable) {
        android.util.Log.e("MainActivity", "startFrameServer error: ${t.message}")
    }
}

private fun stopFrameServer() {
    try {
        frameServer?.stop()
        frameServer = null
        android.util.Log.i("MainActivity", "FrameServer stopped")
    } catch (t: Throwable) {
        android.util.Log.e("MainActivity", "stopFrameServer error: ${t.message}")
    }
}
}