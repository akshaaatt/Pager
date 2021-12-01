package com.aemerse.pager

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.*
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.skyfishjy.library.RippleBackground
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), View.OnClickListener {
    var rippleBackground: RippleBackground? = null
    var centerDeviceIcon: ImageView? = null
    var devicePoints = ArrayList<Point>()
    var connectionStatus: TextView? = null
    var wifiManager: WifiManager? = null
    var mManager: WifiP2pManager? = null
    var mChannel: Channel? = null
    var mReceiver: BroadcastReceiver? = null
    var mIntentFilter: IntentFilter? = null
    var customPeers = ArrayList<CustomDevice>()
    private var serverClass: ServerClass? = null
    var clientClass: ClientClass? = null
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        permissions
        initialSetup()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.wifi_toggle) {
            toggleWifiState()
        }
        return super.onOptionsItemSelected(item)
    }

    inner class ServerClass : Thread() {
        var socket: Socket? = null
        var serverSocket: ServerSocket? = null
        override fun run() {
            try {
                serverSocket = ServerSocket(PORT_USED)
                socket = serverSocket!!.accept()
                SocketHandler.socket = socket
                startActivity(Intent(applicationContext, ChatWindow::class.java))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    inner class ClientClass internal constructor(address: InetAddress) : Thread() {
        var socket: Socket = Socket()
        var hostAddress: String = address.hostAddress
        override fun run() {
            try {
                socket.connect(InetSocketAddress(hostAddress, PORT_USED), 500)
                SocketHandler.socket = socket
                startActivity(Intent(applicationContext, ChatWindow::class.java))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mReceiver)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mReceiver, mIntentFilter)
    }

    private fun initialSetup() {
        // layout files
        connectionStatus = findViewById<View>(R.id.connectionStatus) as TextView
        rippleBackground = findViewById<View>(R.id.content) as RippleBackground
        centerDeviceIcon = findViewById<View>(R.id.centerImage) as ImageView
        // add onClick Listeners
        centerDeviceIcon!!.setOnClickListener(this)

        // center button position
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        devicePoints.add(Point(size.x / 2, size.y / 2))
        Log.d("MainActivity", size.x.toString() + "  " + size.y)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        mManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        mChannel = mManager!!.initialize(this, mainLooper, null)
        mReceiver = WifiDirectBroadcastReceiver(mManager, mChannel!!, this)
        mIntentFilter = IntentFilter()
        mIntentFilter!!.addAction(WIFI_P2P_STATE_CHANGED_ACTION)
        mIntentFilter!!.addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
        mIntentFilter!!.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
        mIntentFilter!!.addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private fun checkLocationEnabled() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) {
        }
        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
        }
        if (!gpsEnabled && !networkEnabled) {
            // notify user
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.gps_network_not_enabled_title)
                .setMessage(R.string.gps_network_not_enabled)
                .setPositiveButton(R.string.open_location_settings) { _, _ ->
                    this@MainActivity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(R.string.Cancel, null)
                .show()
        }
    }

    override fun onClick(v: View) {
        val viewId = v.id
        if (getIndexFromIdPeerList(viewId) != -1) {
            val idx = getIndexFromIdPeerList(viewId)
            val device = customPeers[idx].device
            val config = WifiP2pConfig()
            config.deviceAddress = device!!.deviceAddress
            mManager!!.connect(mChannel, config, object : ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        applicationContext,
                        "Connected to " + device.deviceName,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        applicationContext,
                        "Error in connecting to " + device.deviceName,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } else {
            when (v.id) {
                R.id.centerImage -> {
                    rippleBackground!!.startRippleAnimation()
                    checkLocationEnabled()
                    discoverDevices()
                }
                else -> {}
            }
        }
    }

    private fun getIndexFromIdPeerList(id: Int): Int {
        for (d in customPeers) {
            if (d.id == id) {
                return customPeers.indexOf(d)
            }
        }
        return -1
    }

    private fun checkPeersListByName(deviceName: String): Int {
        for (d in customPeers) {
            if (d.deviceName == deviceName) {
                return customPeers.indexOf(d)
            }
        }
        return -1
    }

    private fun discoverDevices() {
        mManager!!.discoverPeers(mChannel, object : ActionListener {
            override fun onSuccess() {
                connectionStatus!!.text = "Discovery Started"
            }

            override fun onFailure(reason: Int) {
                connectionStatus!!.text = "Discovery start Failed"
            }
        })
    }

    var peerListListener = PeerListListener { peersList ->
        Log.d("DEVICE_NAME", "Listener called" + peersList.deviceList.size)
        if (peersList.deviceList.isNotEmpty()) {

            // first make a list of all devices already present
            val deviceAlreadyPresent = ArrayList<CustomDevice>()
            for (device in peersList.deviceList) {
                val idx = checkPeersListByName(device.deviceName)
                if (idx != -1) {
                    // device already in list
                    deviceAlreadyPresent.add(customPeers[idx])
                }
            }
            if (deviceAlreadyPresent.size == peersList.deviceList.size) {
                // all discovered devices already present
                return@PeerListListener
            }

            // clear previous views
            clearAllDeviceIcons()

            // this will remove all devices no longer in range
            customPeers.clear()
            // add all devices in range
            customPeers.addAll(deviceAlreadyPresent)

            // add all already present devices to the view
            for (d in deviceAlreadyPresent) {
                rippleBackground!!.addView(d.icon_view)
            }
            for (device in peersList.deviceList) {
                if (checkPeersListByName(device.deviceName) == -1) {
                    // device not already present
                    val tmpDevice = createNewDevice(device.deviceName)
                    rippleBackground!!.addView(tmpDevice)
                    foundDevice(tmpDevice)
                    val tmpDeviceObj = CustomDevice()
                    tmpDeviceObj.deviceName = device.deviceName
                    tmpDeviceObj.id = tmpDevice.id
                    tmpDeviceObj.device = device
                    tmpDeviceObj.icon_view = tmpDevice
                    customPeers.add(tmpDeviceObj)
                }
            }
        }
        if (peersList.deviceList.isEmpty()) {
            Toast.makeText(applicationContext, "No Peers Found", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllDeviceIcons() {
        if (customPeers.isNotEmpty()) {
            for (d in customPeers) {
                rippleBackground!!.removeView(findViewById(d.id))
            }
        }
    }

    var connectionInfoListener = ConnectionInfoListener { info ->
        val groupOwnerAddress = info.groupOwnerAddress
        if (info.groupFormed && info.isGroupOwner) {
            connectionStatus!!.text = "HOST"
            serverClass = ServerClass()
            serverClass!!.start()
        } else if (info.groupFormed) {
            connectionStatus!!.text = "CLIENT"
            clientClass = ClientClass(groupOwnerAddress)
            clientClass!!.start()
        }
    }

    private fun generateRandomPosition(): Point {
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val SCREEN_WIDTH = size.x
        val SCREEN_HEIGHT = size.y
        val heightStart = SCREEN_HEIGHT / 2 - 300
        var x: Int
        var y: Int
        do {
            x = (Math.random() * SCREEN_WIDTH).toInt()
            y = (Math.random() * heightStart).toInt()
        } while (checkPositionOverlap(Point(x, y)))
        val newPoint = Point(x, y)
        devicePoints.add(newPoint)
        return newPoint
    }

    private fun checkPositionOverlap(new_p: Point): Boolean {
        //  if overlap, then return true, else return false
        if (devicePoints.isNotEmpty()) {
            for (p in devicePoints) {
                val distance = sqrt(
                    (new_p.x - p.x).toDouble().pow(2.0) + (new_p.y - p.y).toDouble().pow(2.0)
                ).toInt()
                Log.d(TAG, distance.toString() + "")
                if (distance < SEPRATION_DIST_THRESHOLD) {
                    return true
                }
            }
        }
        return false
    }

    private fun createNewDevice(device_name: String?): View {
        val device1 = LayoutInflater.from(this).inflate(R.layout.device_icon, null)
        val newPoint = generateRandomPosition()
        val params = RelativeLayout.LayoutParams(350, 350)
        params.setMargins(newPoint.x, newPoint.y, 0, 0)
        device1.layoutParams = params
        val txtDevice1 = device1.findViewById<TextView>(R.id.myImageViewText)
        val deviceId = System.currentTimeMillis().toInt() + device_count++
        txtDevice1.text = device_name
        device1.id = deviceId
        device1.setOnClickListener(this)
        device1.visibility = View.INVISIBLE
        return device1
    }

    private fun foundDevice(foundDevice: View) {
        val animatorSet = AnimatorSet()
        animatorSet.duration = 400
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        val animatorList = ArrayList<Animator>()
        val scaleXAnimator = ObjectAnimator.ofFloat(foundDevice, "ScaleX", 0f, 1.2f, 1f)
        animatorList.add(scaleXAnimator)
        val scaleYAnimator = ObjectAnimator.ofFloat(foundDevice, "ScaleY", 0f, 1.2f, 1f)
        animatorList.add(scaleYAnimator)
        animatorSet.playTogether(animatorList)
        foundDevice.visibility = View.VISIBLE
        animatorSet.start()
    }

    private fun toggleWifiState() {
        if (wifiManager!!.isWifiEnabled) {
            wifiManager!!.isWifiEnabled = false
            menu!!.findItem(R.id.wifi_toggle).title = "Turn Wifi On"
        } else {
            wifiManager!!.isWifiEnabled = true
            menu!!.findItem(R.id.wifi_toggle).title = "Turn Wifi Off"
        }
    }

    private val permissions: Unit
        get() {
            if ((ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
                        != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                        != PackageManager.PERMISSION_GRANTED)
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    MY_PERMISSIONS_REQUEST_REQUIRED_PERMISSION
                )
            }
        }

    companion object {
        private const val TAG = "MainActivity"
        private const val MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1
        private const val MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 2
        private const val MY_PERMISSIONS_REQUEST_REQUIRED_PERMISSION = 3
        private const val SEPRATION_DIST_THRESHOLD = 50
        private var device_count = 0
        const val PORT_USED = 9584
    }
}