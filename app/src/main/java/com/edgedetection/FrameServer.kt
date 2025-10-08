package com.edgedetection

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Method
import java.util.concurrent.atomic.AtomicReference

class FrameServer(port: Int) : NanoHTTPD(port) {
    companion object {
        private const val TAG = "FrameServer"
    }

    // Callback to apply settings received from web viewer
    var onSettings: ((Int, Int, Boolean) -> Unit)? = null

    // Latest processed frame as JPEG bytes
    private val latestJpeg: AtomicReference<ByteArray?> = AtomicReference(null)
    // Latest status text
    private val latestStatus: AtomicReference<String> = AtomicReference("idle")

    fun updateFrameJpeg(jpeg: ByteArray?) {
        latestJpeg.set(jpeg)
    }

    fun updateStatus(status: String) {
        latestStatus.set(status)
    }

    override fun serve(session: IHTTPSession): Response {
        // Handle CORS preflight
        if (session.method == Method.OPTIONS) {
            val res = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            addCors(res)
            return res
        }
        return try {
            val uri = session.uri
            when (uri) {
                "/frame.jpg" -> serveFrame()
                "/status" -> serveStatus()
                "/settings" -> handleSettings(session)
                else -> okText("Edge server running")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "serve error: ${t.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error")
        }
    }

    private fun okText(body: String): Response {
        val res = newFixedLengthResponse(Response.Status.OK, "text/plain", body)
        addCors(res)
        return res
    }

    private fun serveStatus(): Response {
        val res = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"${latestStatus.get()}\"}")
        addCors(res)
        return res
    }

    private fun serveFrame(): Response {
        val data = latestJpeg.get()
        if (data == null) {
            val res = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "no frame")
            addCors(res)
            return res
        }
        val res = newFixedLengthResponse(Response.Status.OK, "image/jpeg", data.inputStream(), data.size.toLong())
        addCors(res)
        return res
    }

    private fun handleSettings(session: IHTTPSession): Response {
        // Accept JSON with lowThreshold, highThreshold, edgesEnabled
        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val body = map["postData"] ?: ""
            Log.d(TAG, "settings body: $body")
            val low = extractInt(body, "lowThreshold")
            val high = extractInt(body, "highThreshold")
            val enabled = extractBoolean(body, "edgesEnabled")
            onSettings?.invoke(low ?: 0, high ?: 0, enabled ?: true)
            val res = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
            addCors(res)
            res
        } catch (t: Throwable) {
            Log.e(TAG, "handleSettings error: ${t.message}")
            val res = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"ok\":false}")
            addCors(res)
            res
        }
    }

    private fun extractInt(json: String, key: String): Int? {
        // Simple regex-like parsing to avoid adding a JSON dependency
        val idx = json.indexOf("\"$key\"")
        if (idx < 0) return null
        val colon = json.indexOf(":", idx)
        if (colon < 0) return null
        var end = colon + 1
        while (end < json.length && json[end].isWhitespace()) end++
        val sb = StringBuilder()
        while (end < json.length && (json[end].isDigit())) {
            sb.append(json[end])
            end++
        }
        return sb.toString().toIntOrNull()
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val idx = json.indexOf("\"$key\"")
        if (idx < 0) return null
        val colon = json.indexOf(":", idx)
        if (colon < 0) return null
        var end = colon + 1
        while (end < json.length && json[end].isWhitespace()) end++
        return when {
            json.startsWith("true", end) -> true
            json.startsWith("false", end) -> false
            else -> null
        }
    }

    private fun addCors(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
    }
}