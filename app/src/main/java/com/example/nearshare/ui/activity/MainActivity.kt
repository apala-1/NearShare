package com.example.nearshare.ui.activity

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import com.example.nearshare.R

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickFile: Button
    private lateinit var btnStartServer: Button
    private lateinit var btnDownload: Button
    private lateinit var tvHotspotInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private var fileToSend: File? = null
    private var httpServer: SimpleHttpServer? = null

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, "send_file")
            inputStream?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            fileToSend = tempFile
            toast("File selected. Ready to share.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPickFile = findViewById(R.id.btnPickFile)
        btnStartServer = findViewById(R.id.btnStartServer)
        btnDownload = findViewById(R.id.btnDownload)
        tvHotspotInfo = findViewById(R.id.tvHotspotInfo)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        btnPickFile.setOnClickListener { pickFile.launch("*/*") }

        btnStartServer.setOnClickListener { startHttpServer() }
        btnDownload.setOnClickListener { downloadFile("http://192.168.43.1:8080/send_file") }
    }

    private fun startHttpServer() {
        val file = fileToSend
        if (file == null) {
            toast("Select a file first")
            return
        }

        httpServer = SimpleHttpServer(8080, file)
        try {
            httpServer?.start()
            toast("HTTP server running at http://192.168.43.1:8080")
            tvHotspotInfo.text =
                "⚠️ Make sure your hotspot is ON and connected.\nSSID: YourHotspotSSID\nPassword: YourHotspotPassword"
        } catch (e: Exception) {
            toast("Server error: ${e.message}")
        }
    }

    private fun downloadFile(urlStr: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()
                val total = conn.contentLength
                val input = conn.inputStream
                val outFile = File(cacheDir, "received_file")
                val output = FileOutputStream(outFile)
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var done = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    done += bytesRead
                    val percent = ((done * 100) / total)
                    runOnUiThread {
                        progressBar.progress = percent
                        progressText.text = "$percent%"
                    }
                }
                output.flush()
                output.close()
                input.close()
                runOnUiThread { toast("Download complete: ${outFile.absolutePath}") }
            } catch (e: Exception) {
                runOnUiThread { toast("Download error: ${e.message}") }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        httpServer?.stop()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    class SimpleHttpServer(port: Int, private val file: File) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession?): Response {
            val fis = FileInputStream(file)
            val resp =
                newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, file.length())
            resp.addHeader("Content-Disposition", "attachment; filename=${file.name}")
            return resp
        }
    }
}
