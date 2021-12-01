package com.aemerse.pager

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat

class WifiDirectBroadcastReceiver(private val mManager: WifiP2pManager?, private val mChannel: WifiP2pManager.Channel, private val mActivity: MainActivity) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action -> {
                when (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                        Toast.makeText(context, "WIFI is On", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(context, "WIFI is OFF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action -> {
                if (mManager != null) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    mManager.requestPeers(mChannel, mActivity.peerListListener)
                    Log.e("DEVICE_NAME", "WIFI P2P peers changed called")
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action -> {
                if (mManager == null) {
                    return
                }
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                when {
                    networkInfo != null && networkInfo.isConnected -> {
                        mManager.requestConnectionInfo(mChannel, mActivity.connectionInfoListener)
                    }
                    else -> {
                        mActivity.connectionStatus!!.text = "Device Disconnected"
                        mActivity.clearAllDeviceIcons()
                        mActivity.rippleBackground!!.stopRippleAnimation()
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action -> {}
        }
    }
}