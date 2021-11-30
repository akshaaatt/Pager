package com.aemerse.pager

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.IBinder
import android.util.Log
import com.aemerse.pager.SocketHandler.socket
import java.io.IOException

class AudioStreamingService : Service() {
    private var keepPlaying = true
    private var audioTrack: AudioTrack? = null

    override fun onBind(intent: Intent): IBinder? {
        // TODO: Return the communication channel to the service.
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startStreaming()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        keepPlaying = false
        if (audioTrack != null) audioTrack!!.release()
    }

    private fun startStreaming() {
        val audioPlayerRunnable = Runnable {
            var bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
                bufferSize = SAMPLE_RATE * 2
            }
            Log.d("PLAY", "buffersize = $bufferSize")
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack!!.play()
            Log.v("PLAY", "Audio streaming started")
            val buffer = ByteArray(bufferSize)
            val offset = 0
            try {
                val inputStream = socket!!.getInputStream()
                var bytes_read = inputStream.read(buffer, 0, bufferSize)
                while (keepPlaying && bytes_read != -1) {
                    audioTrack!!.write(buffer, 0, buffer.size)
                    bytes_read = inputStream.read(buffer, 0, bufferSize)
                }
                inputStream.close()
                audioTrack!!.release()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
        val t = Thread(audioPlayerRunnable)
        t.start()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}