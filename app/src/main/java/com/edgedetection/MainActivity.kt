package com.edgedetection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private external fun stringFromJNI(): String

    companion object {
        init {
            System.loadLibrary("edgedetection")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv: TextView = findViewById(R.id.sample_text)
        tv.text = stringFromJNI()
    }
}