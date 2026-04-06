package com.audiopush.tv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder

class PairingServer(
    private val onPushReceived: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    val serverUrl: String?
        get() = if (isRunning) "http://$localIp:$PORT" else null

    val localIp: String?
        get() = getLocalIpAddress()

    companion object {
        const val PORT = 8899
        private const val TAG = "PairingServer"
    }

    fun start() {
        if (isRunning) return
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                isRunning = true
                Log.i(TAG, "Server started at $serverUrl")
                acceptConnections()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start server", e)
            }
        }
    }

    private suspend fun acceptConnections() {
        while (isRunning) {
            try {
                val client = serverSocket?.accept() ?: break
                scope.launch(Dispatchers.IO) {
                    handleRequest(client)
                }
            } catch (e: IOException) {
                if (isRunning) Log.e(TAG, "Error accepting connection", e)
            }
        }
    }

    private fun handleRequest(client: java.net.Socket) {
        try {
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return

            val method = requestLine.split(" ")[0]
            val path = requestLine.split(" ")[1].substringBefore("?")

            // CORS preflight
            if (method == "OPTIONS") {
                sendResponse(client, 200, "OK", "")
                return
            }

            when {
                path == "/" || path == "/index.html" -> {
                    val html = getPushPage()
                    sendResponse(client, 200, "OK", html, "text/html; charset=utf-8")
                }
                path == "/push" && method == "POST" -> {
                    val contentLength = requestLine.let {
                        reader.readLines().find { line -> line.startsWith("Content-Length:") }
                            ?.split(":")?.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    }
                    val body = reader.readLine()
                    handlePush(client, body)
                }
                path == "/status" -> {
                    val json = """{"running":true,"serverUrl":"$serverUrl"}"""
                    sendResponse(client, 200, "OK", json, "application/json")
                }
                else -> {
                    sendResponse(client, 404, "Not Found", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
        } finally {
            try { client.close() } catch (e: Exception) {}
        }
    }

    private fun handlePush(client: java.net.Socket, body: String?) {
        try {
            val url = extractUrlFromJson(body ?: "")
            if (url.isNotEmpty()) {
                onPushReceived(url)
                sendResponse(client, 200, "OK", """{"success":true,"message":"推送成功"}""", "application/json")
            } else {
                sendResponse(client, 400, "Bad Request", """{"success":false,"message":"URL不能为空"}""", "application/json")
            }
        } catch (e: Exception) {
            sendResponse(client, 400, "Bad Request", """{"success":false,"message":"解析失败"}""", "application/json")
        }
    }

    private fun extractUrlFromJson(json: String): String {
        val urlPattern = """"url"\s*:\s*"([^"]+)"""".toRegex()
        return urlPattern.find(json)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun sendResponse(client: java.net.Socket, statusCode: Int, status: String, body: String, contentType: String = "text/plain") {
        val response = buildString {
            append("HTTP/1.1 $statusCode $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.toByteArray().size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("\r\n")
            append(body)
        }
        client.getOutputStream().write(response.toByteArray())
    }

    private fun getPushPage(): String {
        val safeIp = escapeHtml(localIp ?: "Unknown")
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AudioPush - 推送到电视</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .container {
      background: rgba(255,255,255,0.95);
      border-radius: 24px;
      padding: 40px;
      width: 100%;
      max-width: 480px;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    }
    .header { text-align: center; margin-bottom: 32px; }
    .icon { font-size: 64px; margin-bottom: 16px; }
    h1 { font-size: 28px; color: #1a1a2e; margin-bottom: 8px; }
    .subtitle { color: #666; font-size: 16px; }
    .input-group { margin-bottom: 24px; }
    label { display: block; font-size: 14px; color: #666; margin-bottom: 8px; }
    input {
      width: 100%;
      padding: 16px;
      font-size: 16px;
      border: 2px solid #e0e0e0;
      border-radius: 12px;
      outline: none;
    }
    input:focus { border-color: #007AFF; }
    button {
      width: 100%;
      padding: 16px;
      font-size: 18px;
      font-weight: 600;
      color: white;
      background: linear-gradient(135deg, #007AFF 0%, #5856D6 100%);
      border: none;
      border-radius: 12px;
      cursor: pointer;
    }
    button:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(0,122,255,0.4); }
    button:disabled { background: #ccc; cursor: not-allowed; transform: none; box-shadow: none; }
    .status {
      margin-top: 20px;
      padding: 16px;
      border-radius: 12px;
      text-align: center;
      display: none;
    }
    .status.success { display: block; background: #E8F5E9; color: #2E7D32; }
    .status.error { display: block; background: #FFEBEE; color: #C62828; }
    .status.loading { display: block; background: #E3F2FD; color: #1565C0; }
    .hint {
      margin-top: 24px;
      padding: 16px;
      background: #F5F5F5;
      border-radius: 12px;
      font-size: 13px;
      color: #666;
    }
    .tv-info { text-align: center; margin-top: 20px; font-size: 12px; color: #999; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div class="icon">📺</div>
      <h1>AudioPush</h1>
      <p class="subtitle">推送到电视播放</p>
    </div>
    <div class="input-group">
      <label>播客链接</label>
      <input type="text" id="urlInput" placeholder="粘贴小宇宙播客链接..." autofocus>
    </div>
    <button id="pushBtn" onclick="pushUrl()">推 送</button>
    <div id="status" class="status"></div>
    <div class="hint">
      <strong>支持格式：</strong>
      <ul style="margin-left:20px;margin-top:8px;">
        <li>小宇宙播客主页</li>
        <li>小宇宙单集</li>
      </ul>
    </div>
    <div class="tv-info">电视端：$safeIp</div>
  </div>
  <script>
    const urlInput = document.getElementById('urlInput');
    const pushBtn = document.getElementById('pushBtn');
    const status = document.getElementById('status');

    urlInput.addEventListener('paste', () => setTimeout(pushUrl, 100));
    urlInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') pushUrl(); });

    async function pushUrl() {
      const url = urlInput.value.trim();
      if (!url) { showStatus('请输入播客链接', 'error'); return; }
      if (!url.includes('xiaoyuzhoufm.com')) { showStatus('仅支持小宇宙播客', 'error'); return; }

      showStatus('正在推送...', 'loading');
      pushBtn.disabled = true;

      try {
        const res = await fetch('/push', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url, ts: Date.now() })
        });
        const result = await res.json();
        if (res.ok && result.success) {
          showStatus('✓ 推送成功！', 'success');
          urlInput.value = '';
        } else {
          showStatus('推送失败：' + (result.message || '未知错误'), 'error');
        }
      } catch (e) {
        showStatus('推送失败：' + e.message, 'error');
      } finally {
        pushBtn.disabled = false;
      }
    }

    function showStatus(msg, type) {
      status.textContent = msg;
      status.className = 'status ' + type;
    }
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        return null
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        serverSocket = null
    }
}
