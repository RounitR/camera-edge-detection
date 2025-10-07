package com.edgedetection

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EdgeRenderer : GLSurfaceView.Renderer {
    private var glSurfaceView: GLSurfaceView? = null

    companion object {
        private const val TAG = "EdgeRenderer"
        private const val VERTEX_SHADER_CODE = """
            attribute vec4 vPosition;
            attribute vec2 vTexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 fTexCoord;

            void main() {
                gl_Position = uMVPMatrix * vPosition;
                fTexCoord = vTexCoord;
            }
        """
        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec2 fTexCoord;
            uniform sampler2D uTexture;
            uniform float uAlpha;

            void main() {
                vec4 color = texture2D(uTexture, fTexCoord);
                gl_FragColor = vec4(color.rgb, color.a * uAlpha);
            }
        """
        private val QUAD_VERTICES = floatArrayOf(
            // x, y, z,      u, v
            -1.0f, -1.0f, 0.0f,      0.0f, 1.0f,  // Bottom left
             1.0f, -1.0f, 0.0f,      1.0f, 1.0f,  // Bottom right
            -1.0f,  1.0f, 0.0f,      0.0f, 0.0f,  // Top left
             1.0f,  1.0f, 0.0f,      1.0f, 0.0f   // Top right
        )
        private const val COORDS_PER_VERTEX = 3
        private const val TEXTURE_COORDS_PER_VERTEX = 2
        private const val VERTEX_STRIDE = (COORDS_PER_VERTEX + TEXTURE_COORDS_PER_VERTEX) * 4 // 4 bytes per float
    }

    // Shader handles
    private var shaderProgram: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var textureHandle: Int = 0
    private var alphaHandle: Int = 0

    // Vertex buffer
    private lateinit var vertexBuffer: FloatBuffer

    // Texture IDs and double-buffering indices
    private var cameraTextureIds = IntArray(2)
    private var processedTextureIds = IntArray(2)
    private var currentCameraBuffer = 0
    private var currentProcessedBuffer = 0

    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    // Surface and frame metadata
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var showProcessedFrame: Boolean = false

    // Pending frame data (original and processed)
    private var pendingOriginalFrameData: ByteArray? = null
    private var pendingProcessedFrameData: ByteArray? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private var originalRowStride: Int = 0
    private var isOriginalFrameReady: Boolean = false
    private var isProcessedFrameReady: Boolean = false

    // GL state caching
    private var lastBoundTexture: Int = -1
    private var lastUsedProgram: Int = -1
    private var vertexArraysEnabled: Boolean = false

    // Frame synchronization
    private var frameCounter: Long = 0
    private var lastFrameTime: Long = 0
    private val targetFrameTime: Long = 33 // ~30 FPS (33ms per frame)

    init {
        // Initialize vertex buffer
        val bb = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(QUAD_VERTICES)
        vertexBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated")

        // Set clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Create shader program
        shaderProgram = createShaderProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE)

        // Get handles to shader variables
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        alphaHandle = GLES20.glGetUniformLocation(shaderProgram, "uAlpha")

        // Generate textures for double-buffering
        val textures = IntArray(4)
        GLES20.glGenTextures(4, textures, 0)
        cameraTextureIds[0] = textures[0]
        cameraTextureIds[1] = textures[1]
        processedTextureIds[0] = textures[2]
        processedTextureIds[1] = textures[3]

        // Configure all textures
        configureTexture(cameraTextureIds[0])
        configureTexture(cameraTextureIds[1])
        configureTexture(processedTextureIds[0])
        configureTexture(processedTextureIds[1])

        Log.i(TAG, "OpenGL setup complete. Program: $shaderProgram")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: ${width}x${height}")

        surfaceWidth = width
        surfaceHeight = height

        GLES20.glViewport(0, 0, width, height)

        // Calculate projection matrix
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)

        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.d(TAG, "onDrawFrame called")

        // Frame synchronization
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime < targetFrameTime) {
            Log.d(TAG, "Frame skipped due to rate limiting (${currentTime - lastFrameTime}ms < ${targetFrameTime}ms)")
            return // Skip frame to maintain target FPS
        }
        lastFrameTime = currentTime
        frameCounter++

        Log.d(TAG, "Processing frame $frameCounter")

        // Clear the screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Use our shader program (only if different)
        if (lastUsedProgram != shaderProgram) {
            GLES20.glUseProgram(shaderProgram)
            lastUsedProgram = shaderProgram
        }

        // Update texture if new frame data is available
        updateTexture()

        // Determine which texture to use
        val targetTexture = if (showProcessedFrame) {
            Log.d(TAG, "Rendering processed frame, buffer: $currentProcessedBuffer, texture: ${processedTextureIds[currentProcessedBuffer]}")
            processedTextureIds[currentProcessedBuffer]
        } else {
            Log.d(TAG, "Rendering camera frame, buffer: $currentCameraBuffer, texture: ${cameraTextureIds[currentCameraBuffer]}")
            cameraTextureIds[currentCameraBuffer]
        }

        // Bind texture only if different from last bound
        if (lastBoundTexture != targetTexture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTexture)
            lastBoundTexture = targetTexture
        }

        // Draw the quad
        drawQuad()

        // Check for OpenGL errors (less frequently for performance)
        if (frameCounter % 60 == 0L) {
            checkGLError("onDrawFrame")
        }
    }

    private fun createShaderProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        // Check link status
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Error linking program: $error")
            GLES20.glDeleteProgram(program)
            return 0
        }

        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Check compilation status
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Error compiling shader: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    private fun configureTexture(textureId: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun updateTexture() {
        synchronized(this) {
            // Update original frame texture using double-buffering
            if (isOriginalFrameReady && pendingOriginalFrameData != null && frameWidth > 0 && frameHeight > 0) {
                // Switch to next buffer for upload
                val uploadBuffer = (currentCameraBuffer + 1) % 2
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTextureIds[uploadBuffer])

                // Convert grayscale data (Y plane, possibly strided) to RGBA for OpenGL
                val rgbaData = if (originalRowStride != 0 && originalRowStride != frameWidth) {
                    Log.d(TAG, "Converting original frame with stride: rowStride=$originalRowStride width=$frameWidth height=$frameHeight")
                    convertGrayscaleWithStrideToRGBA(pendingOriginalFrameData!!, frameWidth, frameHeight, originalRowStride)
                } else {
                    convertGrayscaleToRGBA(pendingOriginalFrameData!!)
                }
                val buffer = ByteBuffer.allocateDirect(rgbaData.size)
                buffer.put(rgbaData)
                buffer.position(0)

                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    frameWidth, frameHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                )

                // Switch to the newly uploaded buffer for rendering
                currentCameraBuffer = uploadBuffer
                isOriginalFrameReady = false
            }

            // Update processed frame texture using double-buffering
            if (isProcessedFrameReady && pendingProcessedFrameData != null && frameWidth > 0 && frameHeight > 0) {
                Log.d(TAG, "Uploading processed frame texture: ${pendingProcessedFrameData!!.size} bytes")
                // Switch to next buffer for upload
                val uploadBuffer = (currentProcessedBuffer + 1) % 2
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureIds[uploadBuffer])

                // Convert grayscale data to RGBA for OpenGL (processed data is contiguous width*height)
                val rgbaData = convertGrayscaleToRGBA(pendingProcessedFrameData!!)
                Log.d(TAG, "Converted to RGBA: ${rgbaData.size} bytes")
                val buffer = ByteBuffer.allocateDirect(rgbaData.size)
                buffer.put(rgbaData)
                buffer.position(0)

                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    frameWidth, frameHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                )

                Log.d(TAG, "Texture uploaded successfully, switching to buffer $uploadBuffer")
                // Switch to the newly uploaded buffer for rendering
                currentProcessedBuffer = uploadBuffer
                isProcessedFrameReady = false
            }
        }
    }

    private fun convertGrayscaleToRGBA(grayscaleData: ByteArray): ByteArray {
        val rgbaData = ByteArray(grayscaleData.size * 4)
        for (i in grayscaleData.indices) {
            val gray = grayscaleData[i].toInt() and 0xFF
            val baseIndex = i * 4
            rgbaData[baseIndex] = gray.toByte()     // R
            rgbaData[baseIndex + 1] = gray.toByte() // G
            rgbaData[baseIndex + 2] = gray.toByte() // B
            rgbaData[baseIndex + 3] = 255.toByte()  // A
        }
        return rgbaData
    }

    // Stride-aware conversion for Y plane (rowStride may be > width)
    private fun convertGrayscaleWithStrideToRGBA(grayscaleData: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rgbaData = ByteArray(width * height * 4)
        var srcIndex = 0
        var dstIndex = 0
        for (row in 0 until height) {
            srcIndex = row * rowStride
            for (col in 0 until width) {
                val gray = grayscaleData[srcIndex + col].toInt() and 0xFF
                rgbaData[dstIndex] = gray.toByte()     // R
                rgbaData[dstIndex + 1] = gray.toByte() // G
                rgbaData[dstIndex + 2] = gray.toByte() // B
                rgbaData[dstIndex + 3] = 255.toByte()  // A
                dstIndex += 4
            }
        }
        return rgbaData
    }

    private fun drawQuad() {
        Log.d(TAG, "drawQuad: Starting to draw quad")

        // Enable vertex arrays only if not already enabled
        if (!vertexArraysEnabled) {
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            checkGLError("Enable vertex arrays")

            // Prepare the coordinate data
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(
                positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertexBuffer
            )
            checkGLError("Set position attribute")

            // Prepare the texture coordinate data
            vertexBuffer.position(COORDS_PER_VERTEX)
            GLES20.glVertexAttribPointer(
                texCoordHandle, TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertexBuffer
            )
            checkGLError("Set texture coordinate attribute")

            vertexArraysEnabled = true
        }

        // Set uniforms
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        checkGLError("Set MVP matrix uniform")
        GLES20.glUniform1i(textureHandle, 0) // Use texture unit 0
        checkGLError("Set texture uniform")
        GLES20.glUniform1f(alphaHandle, 1.0f) // Full opacity
        checkGLError("Set alpha uniform")

        // Draw the quad
        Log.d(TAG, "drawQuad: Drawing triangle strip with 4 vertices")
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGLError("Draw arrays")

        Log.d(TAG, "drawQuad: Completed drawing quad")
    }

    private fun checkGLError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$operation: glError $error")
        }
    }

    // Public methods for updating frame data
    fun updateOriginalFrame(frameData: ByteArray, width: Int, height: Int, rowStride: Int) {
        synchronized(this) {
            Log.d(TAG, "updateOriginalFrame: ${frameData.size} bytes, ${width}x${height}, rowStride=$rowStride")
            pendingOriginalFrameData = frameData
            frameWidth = width
            frameHeight = height
            originalRowStride = rowStride
            isOriginalFrameReady = true
        }
        // Ensure requestRender runs on UI thread to avoid threading violations
        glSurfaceView?.post { glSurfaceView?.requestRender() }
    }

    fun updateProcessedFrame(frameData: ByteArray, width: Int, height: Int) {
        synchronized(this) {
            Log.d(TAG, "updateProcessedFrame: ${frameData.size} bytes, ${width}x${height}")
            pendingProcessedFrameData = frameData
            frameWidth = width
            frameHeight = height
            isProcessedFrameReady = true
        }
        // Ensure requestRender runs on UI thread to avoid threading violations
        glSurfaceView?.post { glSurfaceView?.requestRender() }
    }

    fun setShowProcessedFrame(show: Boolean) {
        Log.d(TAG, "setShowProcessedFrame: $show")
        showProcessedFrame = show
    }

    fun setGLSurfaceView(surfaceView: GLSurfaceView) {
        glSurfaceView = surfaceView
    }
}