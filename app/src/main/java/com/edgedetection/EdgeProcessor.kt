package com.edgedetection

import android.graphics.Bitmap

/**
 * EdgeProcessor class provides native edge detection functionality
 * using OpenCV through JNI bridge
 */
class EdgeProcessor {
    
    /**
     * Process a bitmap and return the edge-detected result
     * @param bitmap Input bitmap to process
     * @return Processed bitmap with edge detection applied
     */
    external fun processFrame(bitmap: Bitmap): Bitmap?
    
    companion object {
        init {
            System.loadLibrary("edgedetection")
        }
        
        /**
         * Initialize OpenCV library
         * @return true if initialization was successful, false otherwise
         */
        @JvmStatic
        external fun initializeOpenCV(): Boolean
    }
}