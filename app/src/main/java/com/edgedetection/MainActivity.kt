package com.edgedetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 200
        
        init {
            System.loadLibrary("edgedetection")
        }
    }

    private lateinit var textureView: TextureView
    private lateinit var toggleButton: Button
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isEdgeDetectionEnabled = false
    
    // Frame capture components
    private var imageReader: ImageReader? = null
    private val frameWidth = 640
    private val frameHeight = 480
    
    // Processing thread components
    private var processingThread: HandlerThread? = null
    private var processingHandler: Handler? = null
    private val frameQueue: BlockingQueue<FrameData> = LinkedBlockingQueue(3) // Limit queue size
    
    // Performance monitoring
    private val frameCount = AtomicLong(0)
    private val processedFrameCount = AtomicLong(0)
    private var lastFpsTime = System.currentTimeMillis()
    private var lastProcessTime = System.currentTimeMillis()
    private val targetFps = 15.0 // Target 15 FPS
    private val minFrameInterval = (1000.0 / targetFps).toLong() // ~67ms between frames
    
    // Frame data class for queue
    data class FrameData(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val rowStride: Int,
        val pixelStride: Int,
        val timestamp: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        toggleButton = findViewById(R.id.toggleButton)

        toggleButton.setOnClickListener {
            isEdgeDetectionEnabled = !isEdgeDetectionEnabled
            toggleButton.text = if (isEdgeDetectionEnabled) "Disable Edge Detection" else "Enable Edge Detection"
        }

        // Initialize OpenCV
        val openCvInitialized = EdgeProcessor.initializeOpenCV()
        
        // Test JNI connectivity
        val jniTest = stringFromJNI()
        android.util.Log.i("MainActivity", "JNI Test: $jniTest")
        android.util.Log.i("MainActivity", "OpenCV Initialized: $openCvInitialized")
        
        // Setup ImageReader for frame capture
        setupImageReader()
        
        // Start processing thread
        startProcessingThread()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        stopProcessingThread()
        super.onPause()
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            return
        }

        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
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
        imageReader = ImageReader.newInstance(frameWidth, frameHeight, ImageFormat.YUV_420_888, 2)
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
        if (!isEdgeDetectionEnabled) return
        
        val currentTime = System.currentTimeMillis()
        frameCount.incrementAndGet()
        
        // FPS limiting - skip frame if too soon
        if (currentTime - lastProcessTime < minFrameInterval) {
            return
        }
        
        try {
            // Extract Y plane (grayscale) from YUV_420_888
            val planes = image.planes
            val yPlane = planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)
            
            // Create frame data
            val frameData = FrameData(
                yArray,
                image.width,
                image.height,
                yPlane.rowStride,
                yPlane.pixelStride,
                currentTime
            )
            
            // Add to queue (non-blocking, drops frame if queue is full)
            if (!frameQueue.offer(frameData)) {
                android.util.Log.d("MainActivity", "Frame queue full, dropping frame")
            }
            
            lastProcessTime = currentTime
            
            // Log performance metrics every 2 seconds
            if (currentTime - lastFpsTime >= 2000) {
                val totalFrames = frameCount.get()
                val processedFrames = processedFrameCount.get()
                val inputFps = totalFrames * 1000.0 / (currentTime - lastFpsTime + 2000)
                val processingFps = processedFrames * 1000.0 / (currentTime - lastFpsTime + 2000)
                
                android.util.Log.i("MainActivity", 
                    "Performance - Input FPS: %.1f, Processing FPS: %.1f, Queue size: %d"
                    .format(inputFps, processingFps, frameQueue.size))
                
                frameCount.set(0)
                processedFrameCount.set(0)
                lastFpsTime = currentTime
            }
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error processing frame: ${e.message}")
        }
    }

    private fun createCameraPreview() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)
            val imageReaderSurface = imageReader?.surface

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)
            imageReaderSurface?.let { captureRequestBuilder?.addTarget(it) }

            val surfaces = mutableListOf(surface)
            imageReaderSurface?.let { surfaces.add(it) }

            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        updatePreview(captureRequestBuilder)
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

    private fun updatePreview(captureRequestBuilder: CaptureRequest.Builder?) {
        if (cameraDevice == null) return
        try {
            captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureSession?.setRepeatingRequest(captureRequestBuilder?.build()!!, null, backgroundHandler)
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
                // Take frame from queue (blocking)
                val frameData = frameQueue.take()
                
                // Process the frame
                processFrameNative(
                    frameData.data,
                    frameData.width,
                    frameData.height,
                    frameData.rowStride,
                    frameData.pixelStride
                )
                
                processedFrameCount.incrementAndGet()
                
                // Continue processing
                processingHandler?.post(this)
                
            } catch (e: InterruptedException) {
                android.util.Log.d("MainActivity", "Processing thread interrupted")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in processing thread: ${e.message}")
                // Continue processing even after error
                processingHandler?.post(this)
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
    external fun stringFromJNI(): String
    
    /**
     * Native method to process frame data
     */
    external fun processFrameNative(
        frameData: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    )
}