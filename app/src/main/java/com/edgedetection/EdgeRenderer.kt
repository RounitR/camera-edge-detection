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
            // x,    y,    z,      u,   v
            -1.0f, -1.0f, 0.0f,      0.0f, 0.0f,  // Bottom left
             1.0f, -1.0f, 0.0f,      1.0f, 0.0f,  // Bottom right
            -1.0f,  1.0f, 0.0f,      0.0f, 1.0f,  // Top left
             1.0f,  1.0f, 0.0f,      1.0f, 1.0f   // Top right
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
    private var hasCameraTexture = false
    private var hasProcessedTexture = false
    private var cameraAllocatedWidth = IntArray(2)
    private var cameraAllocatedHeight = IntArray(2)
    private var processedAllocatedWidth = IntArray(2)
    private var processedAllocatedHeight = IntArray(2)
    private var scaleModeFill = true // center-crop: fill screen

    // Reusable buffers to reduce allocations per frame
    private var cameraRgbaBuffer: ByteArray? = null
    private var processedRgbaBuffer: ByteArray? = null
    private var cameraUploadByteBuffer: ByteBuffer? = null
    private var processedUploadByteBuffer: ByteBuffer? = null

    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    // Add model and aspect matrices
    private val modelMatrix = FloatArray(16)
    private val baseMvpMatrix = FloatArray(16)
    private val aspectScaleMatrix = FloatArray(16)
    private var rotationDegrees: Int = 0
    private var mirrorX: Boolean = false
    // Add vertical mirror support
    private var mirrorY: Boolean = false

    // Surface and frame metadata
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var showProcessedFrame: Boolean = false
    // Simple toggle without complex crossfade
    private var requireFreshProcessedAfterToggle: Boolean = false
    private var freshProcessedAvailable: Boolean = false

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
        // Initialize matrices
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(aspectScaleMatrix, 0)
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

        // Simplify to identity matrices for 2D quad rendering
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
    
        // Base MVP = Identity
        Matrix.setIdentityM(baseMvpMatrix, 0)
    
        // Recompute aspect scaling when surface changes
        updateAspectScale()
    }

    override fun onDrawFrame(gl: GL10?) {
        // Log removed to reduce jank/flicker
        lastFrameTime = System.currentTimeMillis()
        frameCounter++

        // Clear the screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Use our shader program (only if different)
        if (lastUsedProgram != shaderProgram) {
            GLES20.glUseProgram(shaderProgram)
            lastUsedProgram = shaderProgram
        }

        // Update texture if new frame data is available
        updateTexture()

        // Determine which texture to use (simple logic without crossfade)
        val useProcessed = showProcessedFrame && hasProcessedTexture
        val targetTexture = if (useProcessed) {
            processedTextureIds[currentProcessedBuffer]
        } else {
            cameraTextureIds[currentCameraBuffer]
        }
        // Debug: periodically log selection state to diagnose flicker
        if (frameCounter % 120L == 0L) {
            Log.d(TAG, "onDrawFrame: useProcessed=" + useProcessed +
                    ", showProcessedFrame=" + showProcessedFrame +
                    ", hasProcessedTexture=" + hasProcessedTexture +
                    ", requireFresh=" + requireFreshProcessedAfterToggle +
                    ", freshAvailable=" + freshProcessedAvailable +
                    ", hasCameraTexture=" + hasCameraTexture +
                    ", currentProcessedBuffer=" + currentProcessedBuffer +
                    ", currentCameraBuffer=" + currentCameraBuffer)
        }

        // Only render if we have a valid texture corresponding to the selected type
        val canRender = if (useProcessed) hasProcessedTexture else hasCameraTexture
        if (targetTexture > 0 && canRender) {
            // Bind texture only if different from last bound
            if (lastBoundTexture != targetTexture) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTexture)
                lastBoundTexture = targetTexture
            }

            // Set alpha to full opacity
            GLES20.glUniform1f(alphaHandle, 1.0f)

            // Draw the quad
            drawQuad()
        }

        // Clear the fresh requirement once processed is visible
        if (useProcessed && freshProcessedAvailable && requireFreshProcessedAfterToggle) {
            requireFreshProcessedAfterToggle = false
        }

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
            var updated = false
            // Update original frame texture using double-buffering
            // Skip uploading original frames when processed view is active to reduce CPU/GPU load
            if (!showProcessedFrame && isOriginalFrameReady && pendingOriginalFrameData != null && frameWidth > 0 && frameHeight > 0) {
                val uploadBuffer = (currentCameraBuffer + 1) % 2
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTextureIds[uploadBuffer])

                val rgbaSize = frameWidth * frameHeight * 4
                if (cameraRgbaBuffer == null || cameraRgbaBuffer!!.size != rgbaSize) {
                    cameraRgbaBuffer = ByteArray(rgbaSize)
                    cameraUploadByteBuffer = ByteBuffer.allocateDirect(rgbaSize)
                }
                // Fill reusable RGBA buffer from grayscale
                if (originalRowStride != 0 && originalRowStride != frameWidth) {
                    convertGrayscaleWithStrideToRGBA(
                        pendingOriginalFrameData!!,
                        frameWidth,
                        frameHeight,
                        originalRowStride,
                        cameraRgbaBuffer!!
                    )
                } else {
                    convertGrayscaleToRGBA(pendingOriginalFrameData!!, cameraRgbaBuffer!!)
                }
                val buffer = cameraUploadByteBuffer!!
                buffer.position(0)
                buffer.put(cameraRgbaBuffer!!)
                buffer.position(0)

                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
                val needAlloc = cameraAllocatedWidth[uploadBuffer] != frameWidth || cameraAllocatedHeight[uploadBuffer] != frameHeight
                if (needAlloc) {
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        frameWidth, frameHeight, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                    )
                    cameraAllocatedWidth[uploadBuffer] = frameWidth
                    cameraAllocatedHeight[uploadBuffer] = frameHeight
                } else {
                    GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0,
                        0, 0, frameWidth, frameHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                    )
                }

                currentCameraBuffer = uploadBuffer
                isOriginalFrameReady = false
                updated = true
                hasCameraTexture = true
            }

            // Update processed frame texture using double-buffering
            if (isProcessedFrameReady && pendingProcessedFrameData != null && frameWidth > 0 && frameHeight > 0) {
                val uploadBuffer = (currentProcessedBuffer + 1) % 2
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureIds[uploadBuffer])

                val rgbaSize = frameWidth * frameHeight * 4
                if (processedRgbaBuffer == null || processedRgbaBuffer!!.size != rgbaSize) {
                    processedRgbaBuffer = ByteArray(rgbaSize)
                    processedUploadByteBuffer = ByteBuffer.allocateDirect(rgbaSize)
                }
                convertGrayscaleToRGBA(pendingProcessedFrameData!!, processedRgbaBuffer!!)
                val buffer = processedUploadByteBuffer!!
                buffer.position(0)
                buffer.put(processedRgbaBuffer!!)
                buffer.position(0)

                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
                val needAlloc = processedAllocatedWidth[uploadBuffer] != frameWidth || processedAllocatedHeight[uploadBuffer] != frameHeight
                if (needAlloc) {
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        frameWidth, frameHeight, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                    )
                    processedAllocatedWidth[uploadBuffer] = frameWidth
                    processedAllocatedHeight[uploadBuffer] = frameHeight
                } else {
                    GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D, 0,
                        0, 0, frameWidth, frameHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
                    )
                }

                currentProcessedBuffer = uploadBuffer
                isProcessedFrameReady = false
                updated = true
                hasProcessedTexture = true
            }
            if (!updated) {
                // No texture updated; avoid unnecessary state changes
            }
        }
    }

    private fun convertGrayscaleToRGBA(grayscaleData: ByteArray, outRgba: ByteArray) {
        // outRgba must be size width*height*4 matching grayscaleData.size*4
        var dstIndex = 0
        for (i in grayscaleData.indices) {
            val gray = grayscaleData[i].toInt() and 0xFF
            outRgba[dstIndex] = gray.toByte()     // R
            outRgba[dstIndex + 1] = gray.toByte() // G
            outRgba[dstIndex + 2] = gray.toByte() // B
            outRgba[dstIndex + 3] = 255.toByte()  // A
            dstIndex += 4
        }
    }

    // Stride-aware conversion for Y plane (rowStride may be > width)
    private fun convertGrayscaleWithStrideToRGBA(grayscaleData: ByteArray, width: Int, height: Int, rowStride: Int, outRgba: ByteArray) {
        var dstIndex = 0
        for (row in 0 until height) {
            val srcIndex = row * rowStride
            for (col in 0 until width) {
                val gray = grayscaleData[srcIndex + col].toInt() and 0xFF
                outRgba[dstIndex] = gray.toByte()     // R
                outRgba[dstIndex + 1] = gray.toByte() // G
                outRgba[dstIndex + 2] = gray.toByte() // B
                outRgba[dstIndex + 3] = 255.toByte()  // A
                dstIndex += 4
            }
        }
    }

    private fun drawQuad() {
        // Log.d(TAG, "drawQuad: Starting to draw quad")

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

        // Build model matrix with rotation and aspect scale
        Matrix.setIdentityM(modelMatrix, 0)
        // Apply rotation around Z first
        if (rotationDegrees % 360 != 0) {
            val rot = FloatArray(16)
            Matrix.setRotateM(rot, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
            val tmpRot = FloatArray(16)
            Matrix.multiplyMM(tmpRot, 0, rot, 0, modelMatrix, 0)
            System.arraycopy(tmpRot, 0, modelMatrix, 0, 16)
        }
        // Apply mirror for front camera (selfie view)
        if (mirrorX) {
            val mirror = FloatArray(16)
            Matrix.setIdentityM(mirror, 0)
            Matrix.scaleM(mirror, 0, -1f, 1f, 1f)
            val tmpMirror = FloatArray(16)
            Matrix.multiplyMM(tmpMirror, 0, mirror, 0, modelMatrix, 0)
            System.arraycopy(tmpMirror, 0, modelMatrix, 0, 16)
        }
        // Apply vertical mirror when needed (fixes upside-down frames)
        if (mirrorY) {
            val mirror = FloatArray(16)
            Matrix.setIdentityM(mirror, 0)
            Matrix.scaleM(mirror, 0, 1f, -1f, 1f)
            val tmpMirrorY = FloatArray(16)
            Matrix.multiplyMM(tmpMirrorY, 0, mirror, 0, modelMatrix, 0)
            System.arraycopy(tmpMirrorY, 0, modelMatrix, 0, 16)
        }
        // Apply aspect ratio scaling last (scale X,Y to fill)
        val tmpAspect = FloatArray(16)
        Matrix.multiplyMM(tmpAspect, 0, aspectScaleMatrix, 0, modelMatrix, 0)
        System.arraycopy(tmpAspect, 0, modelMatrix, 0, 16)

        // Final MVP = BaseMvp * Model
        Matrix.multiplyMM(mvpMatrix, 0, baseMvpMatrix, 0, modelMatrix, 0)

        // Set uniforms
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        checkGLError("Set MVP matrix uniform")
        GLES20.glUniform1i(textureHandle, 0) // Use texture unit 0
        checkGLError("Set texture uniform")
        // alpha is now set externally per draw call for crossfade

        // Draw the quad
        // Log.d(TAG, "drawQuad: Drawing triangle strip with 4 vertices")
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGLError("Draw arrays")

        // Log.d(TAG, "drawQuad: Completed drawing quad")
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
            try {
                pendingOriginalFrameData = frameData
                frameWidth = width
                frameHeight = height
                originalRowStride = rowStride
                isOriginalFrameReady = true
                updateAspectScale()
            } catch (t: Throwable) {
                Log.e(TAG, "updateOriginalFrame error: ${t.message}")
                isOriginalFrameReady = false
            }
        }
    }

    fun updateProcessedFrame(frameData: ByteArray, width: Int, height: Int) {
        synchronized(this) {
            try {
                pendingProcessedFrameData = frameData
                frameWidth = width
                frameHeight = height
                isProcessedFrameReady = true
                updateAspectScale()
                freshProcessedAvailable = true
            } catch (t: Throwable) {
                Log.e(TAG, "updateProcessedFrame error: ${t.message}")
                isProcessedFrameReady = false
            }
        }
    }

    fun setShowProcessedFrame(show: Boolean) {
        Log.d(TAG, "setShowProcessedFrame: $show")
        showProcessedFrame = show
        requireFreshProcessedAfterToggle = false
    }

    fun setRotationDegrees(degrees: Int) {
        Log.d(TAG, "setRotationDegrees: $degrees (previous: $rotationDegrees)")
        rotationDegrees = degrees
        updateAspectScale()
        // No requestRender; continuous mode
    }

    fun setMirrorX(mirror: Boolean) {
        Log.d(TAG, "setMirrorX: $mirror (previous: $mirrorX)")
        mirrorX = mirror
        // No requestRender; continuous mode
    }

    fun setMirrorY(mirror: Boolean) {
        Log.d(TAG, "setMirrorY: $mirror (previous: $mirrorY)")
        mirrorY = mirror
        // No requestRender; continuous mode
    }

    private fun updateAspectScale() {
        if (surfaceWidth == 0 || surfaceHeight == 0 || frameWidth == 0 || frameHeight == 0) {
            // Not enough info yet
            return
        }
        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()

        // Use frame aspect considering rotation (90/270 swaps width/height)
        val isRotated = (rotationDegrees % 180) != 0
        val effectiveFrameAspect = if (isRotated) {
            frameHeight.toFloat() / frameWidth.toFloat()
        } else {
            frameWidth.toFloat() / frameHeight.toFloat()
        }

        var scaleX: Float
        var scaleY: Float

        if (scaleModeFill) {
            // Center-crop fill: scale the smaller axis up to fill the surface
            if (effectiveFrameAspect > surfaceAspect) {
                // Frame wider than surface -> scale Y up
                scaleX = 1.0f
                scaleY = effectiveFrameAspect / surfaceAspect
            } else {
                // Frame narrower/taller than surface -> scale X up
                scaleX = surfaceAspect / effectiveFrameAspect
                scaleY = 1.0f
            }
        } else {
            // Fit-inside: preserve aspect with letter/pillar boxing
            if (effectiveFrameAspect > surfaceAspect) {
                // Frame wider than surface -> pillarbox horizontally
                scaleX = surfaceAspect / effectiveFrameAspect
                scaleY = 1.0f
            } else {
                // Frame narrower/taller than surface -> letterbox vertically
                scaleX = 1.0f
                scaleY = effectiveFrameAspect / surfaceAspect
            }
        }

        Log.d(TAG, "updateAspectScale: surface ${surfaceWidth}x${surfaceHeight} (aspect=$surfaceAspect), frame ${frameWidth}x${frameHeight} (rot=$rotationDegrees, effAspect=$effectiveFrameAspect), scaleX=$scaleX, scaleY=$scaleY, fillMode=$scaleModeFill")

        Matrix.setIdentityM(aspectScaleMatrix, 0)
        Matrix.scaleM(aspectScaleMatrix, 0, scaleX, scaleY, 1f)
    }

    fun setGLSurfaceView(surfaceView: GLSurfaceView) {
        glSurfaceView = surfaceView
    }
}