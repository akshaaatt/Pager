package com.aemerse.pager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.aemerse.pager.SocketHandler.socket
import com.skyfishjy.library.RippleBackground
import java.io.IOException
import java.io.OutputStream

class ChatWindow : AppCompatActivity(), View.OnClickListener {
    private var sendBtn: Button? = null
    private var rippleBackground: RippleBackground? = null
    private var micRecorder: MicRecorder? = null
    var outputStream: OutputStream? = null
    var t: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_window)
        sendBtn = findViewById<View>(R.id.send_file_btn) as Button
        sendBtn!!.setOnClickListener(this)
        rippleBackground = findViewById<View>(R.id.content) as RippleBackground
        val socket = socket
        try {
            outputStream = socket!!.getOutputStream()
            Log.e("OUTPUT_SOCKET", "SUCCESS")
            startService(Intent(applicationContext, AudioStreamingService::class.java))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.send_file_btn -> if (sendBtn!!.text.toString() == "TALK") {
                // stream audio
                sendBtn!!.text = "OVER"
                micRecorder = MicRecorder()
                t = Thread(micRecorder)
                if (micRecorder != null) {
                    MicRecorder.keepRecording = true
                }
                t!!.start()

                // start animation
                rippleBackground!!.startRippleAnimation()
            } else if (sendBtn!!.text.toString() == "OVER") {
                sendBtn!!.text = "TALK"
                if (micRecorder != null) {
                    MicRecorder.keepRecording = false
                }

                // stop animation
                rippleBackground!!.clearAnimation()
                rippleBackground!!.stopRippleAnimation()
            }
            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (micRecorder != null) {
            MicRecorder.keepRecording = false
        }
    }
}