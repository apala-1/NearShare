package com.example.nearshare.ui.Fragment

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nearshare.R
import com.example.nearshare.ui.adapter.DeviceAdapter
import com.example.nearshare.ui.adapter.DeviceItem
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment() {

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var btnAdvertise: Button
    private lateinit var btnDiscover: Button

    private val SERVICE_ID = "com.example.nearshare"
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT

    private var connectedEndpointId: String? = null
    private var advertising = false
    private var discovering = false
    private var isSender = false

    // File picker for sender
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (!isSender) return@registerForActivityResult
        val endpoint = connectedEndpointId
        if (uri == null) {
            toast("No file selected")
            return@registerForActivityResult
        }
        if (endpoint == null) {
            toast("Not connected to any device")
            return@registerForActivityResult
        }
        sendFileUri(endpoint, uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.recyclerDevices)
        progressBar = view.findViewById(R.id.transferProgressBar)
        progressText = view.findViewById(R.id.progressText)
        btnDiscover = view.findViewById(R.id.receiveBtn)
        btnAdvertise = view.findViewById(R.id.shareBtn)

        progressBar.progress = 0
        progressText.text = "0%"

        deviceAdapter = DeviceAdapter { device ->
            requestConnection(device.endpointId)
        }

        recycler.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        recycler.adapter = deviceAdapter

        connectionsClient = Nearby.getConnectionsClient(requireContext())

        requestNearbyPermissionsIfNeeded()

        btnDiscover.setOnClickListener {
            isSender = false
            startDiscovery()
        }
        btnAdvertise.setOnClickListener {
            isSender = true
            startAdvertising()
        }
    }

    // ---------- Permissions ----------
    private fun requestNearbyPermissionsIfNeeded() {
        val toRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (!hasPerm(Manifest.permission.BLUETOOTH_SCAN)) toRequest += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPerm(Manifest.permission.BLUETOOTH_ADVERTISE)) toRequest += Manifest.permission.BLUETOOTH_ADVERTISE
            if (!hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) toRequest += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) toRequest += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPerm(Manifest.permission.NEARBY_WIFI_DEVICES)) toRequest += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        if (toRequest.isNotEmpty()) requestPermissions(toRequest.toTypedArray(), 42)
    }

    private fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), perm) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // ---------- Advertising / Discovery ----------
    private fun startAdvertising() {
        if (advertising) return
        stopDiscovery()
        val localName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                advertising = true
                toast("Advertising…")
            }
            .addOnFailureListener { e -> toast("Advertise failed: ${e.message}") }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        connectionsClient.stopAdvertising()
        advertising = false
    }

    private fun startDiscovery() {
        if (discovering) return
        stopAdvertising()
        deviceAdapter.clear()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                discovering = true
                toast("Discovering…")
            }
            .addOnFailureListener { e -> toast("Discovery failed: ${e.message}") }
    }

    private fun stopDiscovery() {
        if (!discovering) return
        connectionsClient.stopDiscovery()
        discovering = false
    }

    // ---------- Callbacks ----------
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            deviceAdapter.upsert(DeviceItem(endpointId, info.endpointName ?: "Unknown"))
        }

        override fun onEndpointLost(endpointId: String) {
            deviceAdapter.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Auto-accept
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpointId = endpointId
                    toast("Connected to ${deviceAdapter.get(endpointId)?.name ?: endpointId}")
                    stopDiscovery()

                    if (isSender) {
                        // MIUI-compatible delay to ensure stable connection
                        recycler.postDelayed({ pickFile.launch("*/*") }, 500)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> toast("Connection rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> toast("Connection error")
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedEndpointId == endpointId) connectedEndpointId = null
            toast("Disconnected")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                val file = payload.asFile() ?: return
                val pfd = file.asParcelFileDescriptor() ?: return
                saveIncomingToCache(pfd)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val total = update.totalBytes
                    val done = update.bytesTransferred
                    if (total > 0) {
                        val percent = ((done * 100.0) / total).toInt()
                        progressBar.progress = percent
                        progressText.text = "$percent%"
                    } else {
                        progressText.text = "${update.bytesTransferred} bytes"
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    progressBar.progress = 100
                    progressText.text = "100%"
                    toast("Transfer complete")
                }
                PayloadTransferUpdate.Status.FAILURE -> toast("Transfer failed")
                PayloadTransferUpdate.Status.CANCELED -> toast("Transfer canceled")
            }
        }
    }

    // ---------- Actions ----------
    private fun requestConnection(endpointId: String) {
        val localName = "${Build.MANUFACTURER} ${Build.MODEL}"
        connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener { toast("Requesting connection…") }
            .addOnFailureListener { e -> toast("Request failed: ${e.message}") }
    }

    private fun sendFileUri(endpointId: String, uri: Uri) {
        try {
            val pfd: ParcelFileDescriptor = requireContext().contentResolver.openFileDescriptor(uri, "r") ?: run {
                toast("Could not open file")
                return
            }
            val payload = Payload.fromFile(pfd)
            progressBar.progress = 0
            progressText.text = "0%"
            connectionsClient.sendPayload(endpointId, payload)
        } catch (e: Exception) {
            toast("Send error: ${e.message}")
        }
    }

    private fun saveIncomingToCache(pfd: ParcelFileDescriptor) {
        try {
            val input = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val outFile = File(requireContext().cacheDir, "nearby_${System.currentTimeMillis()}")
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
            toast("Saved to: ${outFile.absolutePath}")
        } catch (e: Exception) { toast("Save error: ${e.message}") }
    }

    override fun onStop() {
        super.onStop()
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
