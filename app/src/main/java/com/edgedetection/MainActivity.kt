package com.edgedetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
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
        EdgeProcessor.initializeOpenCV()
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

    private fun createCameraPreview() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface),
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
}