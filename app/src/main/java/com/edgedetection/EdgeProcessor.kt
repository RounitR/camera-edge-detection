package com.edgedetection

import android.graphics.Bitmap

class EdgeProcessor {
    
    external fun processFrame(bitmap: Bitmap): Bitmap
    
    companion object {
        init {
            System.loadLibrary("edgedetection")
        }
        
        @JvmStatic
        external fun initializeOpenCV(): Boolean
    }
}