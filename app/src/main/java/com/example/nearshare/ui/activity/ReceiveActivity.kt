package com.example.nearshare.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.nearshare.databinding.ActivityReceive2Binding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ReceiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceive2Binding

    private val SERVICE_ID = "com.example.nearshare.SERVICE_ID"
    private val userName = "Receiver" // This represents the local endpoint's name

    // Store the endpoint ID of the connected sender
    private var connectedSenderEndpointId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReceive2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.tvStatusTitle.text = "Waiting for sender..."
        binding.tvFileDetails.text = "No file yet"
        binding.receiveProgress.progress = 0
        binding.receiveProgress.isIndeterminate = true // Start as indeterminate while waiting

        // Cancel button
        binding.btnCancel.setOnClickListener {
            stopAllNearbyConnections()
            binding.tvStatusTitle.text = "Transfer cancelled"
            binding.receiveProgress.progress = 0
            binding.receiveProgress.isIndeterminate = false // Stop indeterminate
            binding.tvFileDetails.text = "No file yet"
            Toast.makeText(this, "Nearby connections stopped.", Toast.LENGTH_SHORT).show()
        }

        // Mock sender button for self-testing
        binding.btnSendTestFile.setOnClickListener {
            // This button triggers a mock sender within the same app instance.
            // For actual testing, you'd typically have a separate sender app/device.
            startMockSender()
            Toast.makeText(this, "Mock sender triggered. This device will try to send to itself.", Toast.LENGTH_LONG).show()
        }

        requestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Crucial: Stop all Nearby Connections operations when the activity is destroyed
        stopAllNearbyConnections()
    }

    /**
     * Stops all active Nearby Connections operations (advertising, discovery, and all endpoints).
     */
    private fun stopAllNearbyConnections() {
        Nearby.getConnectionsClient(this).stopAdvertising()
        Nearby.getConnectionsClient(this).stopDiscovery()
        Nearby.getConnectionsClient(this).stopAllEndpoints()
        connectedSenderEndpointId = null
        Log.d("ReceiveActivity", "Stopped all Nearby Connections operations.")
    }

    /**
     * Requests necessary permissions for Nearby Connections and file storage.
     */
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION, // Needed for older Android versions (pre-12)
            Manifest.permission.READ_EXTERNAL_STORAGE, // For saving files (older Android, pre-11)
            Manifest.permission.WRITE_EXTERNAL_STORAGE // For saving files (older Android, pre-11)
        )

        // Android 12 (API 31) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        // Android 13 (API 33) and above for Wi-Fi P2P scanning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        } else {
            Log.d("ReceiveActivity", "All necessary permissions already granted. Starting advertising.")
            startAdvertising() // Start advertising only if all permissions are already granted
        }
    }

    /**
     * Handles the result of permission requests.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            var allGranted = true
            val deniedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    deniedPermissions.add(permissions[i])
                }
            }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted.", Toast.LENGTH_SHORT).show()
                Log.d("ReceiveActivity", "All permissions granted. Proceeding to start advertising.")
                startAdvertising() // Start advertising after permissions are granted
            } else {
                Toast.makeText(this, "Permissions not granted: ${deniedPermissions.joinToString()}. Cannot receive files.", Toast.LENGTH_LONG).show()
                binding.tvStatusTitle.text = "Permissions Denied"
                Log.e("ReceiveActivity", "Permissions denied: ${deniedPermissions.joinToString()}. Cannot proceed with Nearby Connections.")
            }
        }
    }

    /**
     * Starts advertising to discover nearby senders.
     */
    private fun startAdvertising() {
        binding.tvStatusTitle.text = "Waiting for sender..."
        binding.receiveProgress.isIndeterminate = true // Show indeterminate progress while waiting
        binding.receiveProgress.progress = 0
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        Nearby.getConnectionsClient(this).startAdvertising(
            userName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Toast.makeText(this, "Advertising started as: $userName", Toast.LENGTH_SHORT).show()
            Log.d("ReceiveActivity", "Advertising started successfully with Service ID: $SERVICE_ID")
        }.addOnFailureListener { e ->
            binding.tvStatusTitle.text = "Failed to start advertising"
            Toast.makeText(this, "Failed to start advertising: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("ReceiveActivity", "Start advertising failed", e)
        }
    }

    /**
     * Callback for connection lifecycle events.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("ReceiveActivity", "Connection initiated with ${info.endpointName} ($endpointId)")
            // Automatically accept the connection request for simplicity
            Nearby.getConnectionsClient(applicationContext).acceptConnection(endpointId, payloadCallback)
            connectedSenderEndpointId = endpointId // Store the connected endpoint ID
            runOnUiThread {
                binding.tvStatusTitle.text = "Connecting to ${info.endpointName}..."
                binding.receiveProgress.isIndeterminate = true // Still indeterminate during connection handshake
            }
            Toast.makeText(this@ReceiveActivity, "Connection initiated with ${info.endpointName}", Toast.LENGTH_SHORT).show()
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            runOnUiThread {
                if (result.status.isSuccess) {
                    binding.tvStatusTitle.text = "Connected. Waiting for file..."
                    binding.receiveProgress.isIndeterminate = true // Back to indeterminate while waiting for first payload
                    binding.receiveProgress.progress = 0
                    Toast.makeText(this@ReceiveActivity, "Connection successful!", Toast.LENGTH_SHORT).show()
                    Log.d("ReceiveActivity", "Connection successful with $endpointId")
                } else {
                    binding.tvStatusTitle.text = "Connection failed ❌"
                    binding.receiveProgress.isIndeterminate = false
                    binding.receiveProgress.progress = 0
                    Toast.makeText(this@ReceiveActivity, "Connection failed: ${result.status.statusMessage}", Toast.LENGTH_LONG).show()
                    Log.e("ReceiveActivity", "Connection failed with $endpointId: ${result.status.statusMessage}")
                    connectedSenderEndpointId = null // Reset if connection fails
                    startAdvertising() // Restart advertising to allow new connections
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            runOnUiThread {
                binding.tvStatusTitle.text = "Disconnected"
                binding.tvFileDetails.text = "No file yet"
                binding.receiveProgress.progress = 0
                binding.receiveProgress.isIndeterminate = false
                Toast.makeText(this@ReceiveActivity, "Disconnected from sender.", Toast.LENGTH_SHORT).show()
                Log.d("ReceiveActivity", "Disconnected from $endpointId")
            }
            connectedSenderEndpointId = null
            startAdvertising() // Restart advertising to be ready for new connections
        }
    }

    /**
     * Callback for payload transfer events.
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d("ReceiveActivity", "Payload received from $endpointId, type: ${payload.type}")
            when (payload.type) {
                Payload.Type.FILE -> {
                    // Get the temporary file provided by Nearby Connections
                    val incomingFile = payload.asFile()?.asJavaFile()
                    if (incomingFile != null) {
                        try {
                            // Check if external storage is available for writing
                            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                                // Get the public Downloads directory
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                if (!downloadsDir.exists()) {
                                    downloadsDir.mkdirs() // Create directory if it doesn't exist
                                    Log.d("ReceiveActivity", "Created Downloads directory: ${downloadsDir.absolutePath}")
                                }
                                // Create a new file in the Downloads directory with the original file name
                                val newFile = File(downloadsDir, incomingFile.name)

                                // Copy the contents of the temporary file to the new file
                                incomingFile.copyTo(newFile, overwrite = true)

                                runOnUiThread {
                                    binding.tvFileDetails.text = "File saved: ${newFile.name}"
                                    binding.tvStatusTitle.text = "Transfer complete ✅"
                                    binding.receiveProgress.progress = 100
                                    binding.receiveProgress.isIndeterminate = false
                                    Toast.makeText(this@ReceiveActivity, "File received and saved to Downloads!", Toast.LENGTH_LONG).show()
                                }
                                Log.d("ReceiveActivity", "File saved successfully to: ${newFile.absolutePath}")
                                // Delete the temporary file provided by Nearby Connections as it's no longer needed
                                val deletedTemp = payload.asFile()?.asJavaFile()?.delete()
                                Log.d("ReceiveActivity", "Temporary file deleted: $deletedTemp")
                            } else {
                                runOnUiThread {
                                    binding.tvStatusTitle.text = "Storage not available ❌"
                                    Toast.makeText(this@ReceiveActivity, "External storage not mounted or writable.", Toast.LENGTH_LONG).show()
                                }
                                Log.e("ReceiveActivity", "External storage not mounted, cannot save file.")
                            }
                        } catch (e: IOException) {
                            runOnUiThread {
                                binding.tvStatusTitle.text = "File save failed ❌"
                                Toast.makeText(this@ReceiveActivity, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            Log.e("ReceiveActivity", "Error saving file: ${e.message}", e)
                        }
                    } else {
                        runOnUiThread {
                            binding.tvStatusTitle.text = "Received null file payload ❌"
                            Toast.makeText(this@ReceiveActivity, "Received a null file payload.", Toast.LENGTH_LONG).show()
                        }
                        Log.e("ReceiveActivity", "Received a null file payload.")
                    }
                }
                Payload.Type.BYTES -> {
                    // Handle byte payloads if your app sends them
                    Log.d("ReceiveActivity", "Received byte payload.")
                    payload.asBytes()?.let { bytes ->
                        val receivedString = String(bytes)
                        runOnUiThread {
                            binding.tvFileDetails.text = "Received text: $receivedString"
                            binding.tvStatusTitle.text = "Text received ✅"
                            binding.receiveProgress.progress = 100
                            binding.receiveProgress.isIndeterminate = false
                            Toast.makeText(this@ReceiveActivity, "Text received!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Payload.Type.STREAM -> {
                    // Handle stream payloads if your app sends them
                    Log.d("ReceiveActivity", "Received stream payload (not directly handled in this example).")
                    runOnUiThread {
                        binding.tvStatusTitle.text = "Received stream (not handled) ⚠️"
                        binding.receiveProgress.isIndeterminate = false
                        Toast.makeText(this@ReceiveActivity, "Received stream payload (not handled).", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            runOnUiThread {
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        if (update.totalBytes > 0) {
                            val progress = (100 * update.bytesTransferred / update.totalBytes).toInt()
                            binding.receiveProgress.isIndeterminate = false // Switch to determinate
                            binding.receiveProgress.progress = progress
                            binding.tvStatusTitle.text = "Receiving file... $progress%"
                            Log.d("ReceiveActivity", "Transfer progress: $progress% (Bytes: ${update.bytesTransferred}/${update.totalBytes})")
                        } else {
                            // For unknown total bytes (e.g., streams), show an indeterminate progress
                            binding.receiveProgress.isIndeterminate = true
                            binding.tvStatusTitle.text = "Receiving file..."
                            Log.d("ReceiveActivity", "Transfer in progress (unknown size).")
                        }
                    }
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        Log.d("ReceiveActivity", "Payload transfer SUCCESS for $endpointId. Waiting for file save.")
                        // UI update for completion (100% and "Transfer complete") is handled in onPayloadReceived after saving.
                    }
                    PayloadTransferUpdate.Status.FAILURE -> {
                        binding.tvStatusTitle.text = "Transfer failed ❌"
                        binding.receiveProgress.progress = 0
                        binding.receiveProgress.isIndeterminate = false
                        Toast.makeText(this@ReceiveActivity, "File transfer failed.", Toast.LENGTH_LONG).show()
                        Log.e("ReceiveActivity", "Payload transfer FAILED for $endpointId")
                        connectedSenderEndpointId = null // Reset connected endpoint
                        startAdvertising() // Try to restart advertising
                    }
                    PayloadTransferUpdate.Status.CANCELED -> {
                        binding.tvStatusTitle.text = "Transfer canceled"
                        binding.receiveProgress.progress = 0
                        binding.receiveProgress.isIndeterminate = false
                        Toast.makeText(this@ReceiveActivity, "File transfer canceled.", Toast.LENGTH_SHORT).show()
                        Log.d("ReceiveActivity", "Payload transfer CANCELED for $endpointId")
                        connectedSenderEndpointId = null // Reset connected endpoint
                        startAdvertising() // Try to restart advertising
                    }
                }
            }
        }
    }

    // ------------------- Mock sender for testing (within the same app instance) -------------------
    /**
     * Simulates a sender discovering and sending a file to this receiver within the same app instance.
     * This is primarily for quick self-testing and not a real-world multi-device scenario.
     */
    private fun startMockSender() {
        // Stop any existing advertising/discovery to prevent conflicts with the mock sender role
        stopAllNearbyConnections()
        Log.d("MockSender", "Stopped existing Nearby connections before starting mock sender.")

        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        Nearby.getConnectionsClient(this).startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("MockSender", "Endpoint found: ${info.endpointName} ($endpointId) - This is likely self-discovery.")
                    // Only request connection if not already connected (to avoid multiple attempts)
                    if (connectedSenderEndpointId == null || connectedSenderEndpointId != endpointId) {
                        Log.d("MockSender", "Requesting connection from MockSender to $endpointId")
                        Nearby.getConnectionsClient(this@ReceiveActivity)
                            .requestConnection(
                                "MockSenderName", // Name of the mock sender
                                endpointId,
                                mockSenderConnectionLifecycleCallback // Use a separate callback for mock sender
                            )
                            .addOnSuccessListener { Log.d("MockSender", "Request connection success from MockSender.") }
                            .addOnFailureListener { e -> Log.e("MockSender", "Request connection failed from MockSender.", e) }
                    } else {
                        Log.d("MockSender", "Already connected or connection in progress to $endpointId, skipping request.")
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("MockSender", "Endpoint lost: $endpointId")
                }
            },
            options
        ).addOnSuccessListener {
            Log.d("MockSender", "Discovery started for mock sender.")
            Toast.makeText(this, "Mock Sender: Discovery started...", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Log.e("MockSender", "Failed to start discovery for mock sender: ${e.message}", e)
            Toast.makeText(this, "Mock Sender: Failed to start discovery.", Toast.LENGTH_SHORT).show()
            // Important: If mock sender discovery fails, ensure the receiver advertising is re-enabled
            startAdvertising()
        }

        // Create a temporary test file for the mock sender
        val testFile = File(cacheDir, "MockTestFile_${System.currentTimeMillis()}.txt")
        try {
            FileOutputStream(testFile).use { it.write("This is a test file from the internal mock sender of NearShare. Timestamp: ${System.currentTimeMillis()}".toByteArray()) }
            Log.d("MockSender", "Test file created for mock sender: ${testFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("MockSender", "Error creating test file for mock sender: ${e.message}", e)
            Toast.makeText(this, "Mock Sender: Failed to create test file.", Toast.LENGTH_SHORT).show()
            // Clean up discovery if file creation failed
            Nearby.getConnectionsClient(this).stopDiscovery()
            startAdvertising()
            return
        }

        // Delay the payload sending to allow connection establishment.
        // A more robust approach would be to send the payload from mockSenderConnectionLifecycleCallback.onConnectionResult.
        binding.main.postDelayed({
            if (connectedSenderEndpointId != null) {
                Log.d("MockSender", "Attempting to send payload from MockSender to $connectedSenderEndpointId")
                Nearby.getConnectionsClient(this).sendPayload(
                    connectedSenderEndpointId!!, // Send to the connected endpoint
                    Payload.fromFile(testFile)
                ).addOnSuccessListener {
                    Log.d("MockSender", "Payload send initiated successfully from MockSender.")
                    Toast.makeText(this, "Mock Sender: Payload send initiated.", Toast.LENGTH_SHORT).show()
                    // Delete the temporary file after it's been sent
                    val deleted = testFile.delete()
                    Log.d("MockSender", "Mock test file deleted after sending: $deleted")
                }.addOnFailureListener { e ->
                    Log.e("MockSender", "Failed to send payload from MockSender: ${e.message}", e)
                    Toast.makeText(this, "Mock Sender: Failed to send payload.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w("MockSender", "No endpoint connected for sending mock payload. Connected ID: $connectedSenderEndpointId")
                Toast.makeText(this, "Mock Sender: No connection established to send test file.", Toast.LENGTH_LONG).show()
            }
            // Stop discovery and restart advertising for receiving after the mock send attempt
            Nearby.getConnectionsClient(this).stopDiscovery()
            startAdvertising()

        }, 3000) // Increased delay for better connection reliability in mock scenario
    }

    // Separate ConnectionLifecycleCallback for the Mock Sender's perspective
    // This helps in distinguishing connection events related to receiving vs. mock sending within the same activity.
    private val mockSenderConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("MockSenderCallback", "Mock Sender: Connection initiated with ${info.endpointName} ($endpointId)")
            // Accept the connection immediately from the mock sender's side
            Nearby.getConnectionsClient(applicationContext).acceptConnection(endpointId, mockSenderPayloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {            if (result.status.isSuccess) {
                // IMPORTANT: In a real "sender" activity, you would store the endpointId here
                // and then proceed to send the payload. For this mock, it's just confirming.
                Log.d("MockSenderCallback", "Mock Sender: Connection successful with $endpointId. Ready to send.")
                // No need to set connectedSenderEndpointId here if the main activity's
                // callback already handles it as the receiver.
            } else {
                Log.e("MockSenderCallback", "Mock Sender: Connection failed with $endpointId: ${result.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("MockSenderCallback", "Mock Sender: Disconnected from $endpointId.")
            // No need to restart advertising here, it's handled after the mock send attempt
        }
    }

    // Separate PayloadCallback for the Mock Sender (primarily to monitor its sent payload status)
    private val mockSenderPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d("MockSenderCallback", "Mock Sender: Received payload from $endpointId (unexpected for a sender).")
            // This mock sender only sends, so receiving here is unexpected
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d("MockSenderCallback", "Mock Sender: Payload transfer update for $endpointId, status: ${update.status} (Bytes: ${update.bytesTransferred}/${update.totalBytes})")
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d("MockSenderCallback", "Mock Sender: Payload sent successfully to $endpointId.")
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.e("MockSenderCallback", "Mock Sender: Payload send failed to $endpointId.")
            }
        }
    }
}