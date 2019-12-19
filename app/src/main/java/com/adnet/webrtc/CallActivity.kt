package com.adnet.webrtc

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_call.*
import org.webrtc.*

class CallActivity : AppCompatActivity(),AppRTCClient.SignalingEvents,PeerConnectionClient.PeerConnectionEvents,
    View.OnClickListener {

    private var activityRunning = false
    private var cpuMonitor: CpuMonitor? = null
    private val remoteProxyRenderer= ProxyVideoSink()
    private val localProxyVideoSink= ProxyVideoSink()

    private lateinit var peerConnectionClient: PeerConnectionClient
    private var roomConnectionParameters: AppRTCClient.RoomConnectionParameters? = null
    private lateinit var appRtcClient: AppRTCClient
    private var signalingParameters: AppRTCClient.SignalingParameters? = null
    private lateinit var audioManager: AppRTCAudioManager

    private var iceConnected = false
    private var isError = false
    private var commandLineRun = false
    private var callStartedTimeMs: Long = 0L
//    private var logToast: Toast=Toast.makeText(this@CallActivity, "", Toast.LENGTH_SHORT)
    private var remoteSinks= mutableListOf<VideoSink>()
    private var peerConnectionParameters: PeerConnectionClient.PeerConnectionParameters? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        remoteSinks.add(remoteProxyRenderer)
        button_call_disconnect.setOnClickListener(this)
        check()
    }

    private fun check(){
        // Get Intent parameters.
        val roomId = intent.getStringExtra(EXTRA_ROOMID)
        val loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false)
        val tracing = intent.getBooleanExtra(EXTRA_TRACING, false)
        val videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0)
        val videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0)


        var dataChannelParameters: PeerConnectionClient.DataChannelParameters? = null
        if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
            dataChannelParameters = PeerConnectionClient.DataChannelParameters(
                intent.getBooleanExtra(EXTRA_ORDERED, true),
                intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
                intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1),
                intent.getStringExtra(EXTRA_PROTOCOL),
                intent.getBooleanExtra(EXTRA_NEGOTIATED, false),
                intent.getIntExtra(EXTRA_ID, -1)
            )
        }
        peerConnectionParameters = PeerConnectionClient.PeerConnectionParameters(
            intent.getBooleanExtra(EXTRA_VIDEO_CALL, true),
            loopback,
            tracing,
            videoWidth,
            videoHeight,
            intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
            intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0),
            intent.getStringExtra(EXTRA_VIDEOCODEC),
            intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
            intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
            intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0),
            intent.getStringExtra(EXTRA_AUDIOCODEC),
            intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
            intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
            intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
            intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
            intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
            intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false),
            intent.getBooleanExtra(EXTRA_USE_LEGACY_AUDIO_DEVICE, false),
            dataChannelParameters
        )

        val runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0)

        // For command line execution run connection for <runTimeMs> and exit.
        if (commandLineRun && runTimeMs > 0) {
            Handler().postDelayed({ disconnect() }, runTimeMs.toLong())
        }


        commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false)
        appRtcClient = if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
            WebSocketRTCClient(this)
        } else {
            Log.i(
                TAG,
                "Using DirectRTCClient because room name looks like an IP."
            )
            DirectRTCClient(this)
        }


        val roomUri = intent.data
        if (roomUri == null) {
            logAndToast(getString(R.string.missing_url))
            Log.e(TAG, "Didn't get any URL in intent!")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        Log.d(TAG, "Room ID: $roomId")
        if ( intent.getStringExtra(EXTRA_ROOMID) == null || intent.getStringExtra(EXTRA_ROOMID).isEmpty()) {
            logAndToast(getString(R.string.missing_url))
            Log.e(TAG, "Incorrect room ID in intent!")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS)
        roomConnectionParameters =
            AppRTCClient.RoomConnectionParameters(
                roomUri.toString(),
                roomId,
                loopback,
                urlParameters
            )

        if (CpuMonitor.isSupported()) {
            cpuMonitor = CpuMonitor(this)
        }

        startCall()


    }

    override fun onStart() {
        super.onStart()
        activityRunning = true
        cpuMonitor?.resume()
    }

    override fun onStop() {
        super.onStop()
        activityRunning = false
        cpuMonitor?.pause()
    }

    override fun onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        disconnect()
//        if (logToast != null) {
//            logToast.cancel()
//        }
        activityRunning = false
        super.onDestroy()
    }



    private fun logAndToast(msg: String) {
        Log.d(TAG, msg)

        val logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        logToast.show()
    }

    private fun startCall() {
        callStartedTimeMs = System.currentTimeMillis()
        logAndToast(
            getString(
                R.string.connecting_to,
                roomConnectionParameters?.roomUrl
            )
        )
        appRtcClient.connectToRoom(roomConnectionParameters)
        audioManager = AppRTCAudioManager.create(applicationContext)

        logAndToast("Starting the audio manager...")
        Log.d(TAG, "Starting the audio manager...")
        audioManager.start(object :AppRTCAudioManager.AudioManagerEvents{
            override fun onAudioDeviceChanged(
                selectedAudioDevice: AppRTCAudioManager.AudioDevice,
                availableAudioDevices: MutableSet<AppRTCAudioManager.AudioDevice>
            ) {
                onAudioManagerDevicesChanged(selectedAudioDevice, availableAudioDevices)
            }

        })
    }
    private fun onAudioManagerDevicesChanged(
        device: AppRTCAudioManager.AudioDevice, availableDevices: Set<AppRTCAudioManager.AudioDevice>
    ) {
        Log.d(
            TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                    + "selected: " + device
        )
        // TODO(henrika): add callback handler.
    }

    private fun disconnect() {
        activityRunning = false
        remoteProxyRenderer.setTarget(null)
        localProxyVideoSink.setTarget(null)
        if (appRtcClient != null) {
            appRtcClient!!.disconnectFromRoom()
            //appRtcClient = null
        }
        peerConnectionClient.close()
        audioManager.stop()
        if (iceConnected && !isError) {
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onRemoteIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        runOnUiThread(Runnable {
            peerConnectionClient.removeRemoteIceCandidates(candidates)
        })
    }

    override fun onConnectedToRoom(params: AppRTCClient.SignalingParameters?) {
        val delta: Long = System.currentTimeMillis() - callStartedTimeMs

        signalingParameters = params
        logAndToast("Creating peer connection, delay=" + delta + "ms")
        val videoCapturer: VideoCapturer? = null
        peerConnectionClient.createPeerConnection(
            localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters
        )

        if (signalingParameters!!.initiator) {
            logAndToast("Creating OFFER...")
            peerConnectionClient!!.createOffer()
        } else {
            if (params!!.offerSdp != null) {
                peerConnectionClient!!.setRemoteDescription(params.offerSdp)
                logAndToast("Creating ANSWER...")
                peerConnectionClient!!.createAnswer()
            }
            if (params.iceCandidates != null) {
                for (iceCandidate in params.iceCandidates) {
                    peerConnectionClient!!.addRemoteIceCandidate(iceCandidate)
                }
            }
        }
    }

    override fun onRemoteIceCandidate(candidate: IceCandidate?) {
        runOnUiThread(Runnable {
            if (peerConnectionClient == null) {
                Log.e(
                    TAG,
                    "Received ICE candidate for a non-initialized peer connection."
                )
                return@Runnable
            }
            peerConnectionClient.addRemoteIceCandidate(candidate)
        })
    }

    override fun onChannelError(description: String?) {
      //  TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onChannelClose() {
        runOnUiThread {
            logAndToast("Remote end hung up; dropping PeerConnection")
            disconnect()
        }
    }

    override fun onRemoteDescription(sdp: SessionDescription?) {
        val delta: Long = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread(Runnable {
            if (peerConnectionClient == null) {
                Log.e(
                    TAG,
                    "Received remote SDP for non-initilized peer connection."
                )
                return@Runnable
            }
            logAndToast("Received remote " + sdp!!.type + ", delay=" + delta + "ms")
            peerConnectionClient!!.setRemoteDescription(sdp)
            if (!signalingParameters!!.initiator) {
                logAndToast("Creating ANSWER...")
                peerConnectionClient!!.createAnswer()
            }
        })
    }

    override fun onPeerConnectionClosed() {
      //  TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onIceConnected() {
        val delta: Long = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread {
            logAndToast("ICE connected, delay=" + delta + "ms")
            iceConnected = true
            callConnected()
        }
    }

    private fun callConnected() {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        Log.i(TAG, "Call connected: delay=" + delta + "ms")
        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state")
            return
        }
        peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD)
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        runOnUiThread {
            if (appRtcClient != null) {
                appRtcClient!!.sendLocalIceCandidate(candidate)
            }
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        runOnUiThread {
            if (appRtcClient != null) {
                appRtcClient!!.sendLocalIceCandidateRemovals(candidates)
            }
        }
    }

    override fun onIceDisconnected() {
        runOnUiThread {
          //  logAndToast("ICE disconnected")
            iceConnected = false
            disconnect()
        }
    }

    override fun onPeerConnectionStatsReady(reports: Array<out StatsReport>?) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onLocalDescription(sdp: SessionDescription?) {
        val delta: Long = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread {
            if (appRtcClient != null) {
                logAndToast("Sending " + sdp!!.type + ", delay=" + delta + "ms")
                if (signalingParameters!!.initiator) {
                    appRtcClient!!.sendOfferSdp(sdp)
                } else {
                    appRtcClient!!.sendAnswerSdp(sdp)
                }
            }
            if (peerConnectionParameters!!.videoMaxBitrate > 0) {
                Log.d(
                    TAG,
                    "Set video maximum bitrate: " + peerConnectionParameters!!.videoMaxBitrate
                )
                peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters!!.videoMaxBitrate)
            }
        }
    }

    override fun onPeerConnectionError(description: String) {
        reportError(description)
    }

    private fun reportError(description: String) {
        runOnUiThread {
            if (!isError) {
                isError = true
                logAndToast("LOII...")
            }
        }
    }

    companion object{
        private const val TAG = "CallRTCClient"

        const val EXTRA_ROOMID = "org.appspot.apprtc.ROOMID"
        const val EXTRA_URLPARAMETERS = "org.appspot.apprtc.URLPARAMETERS"
        const val EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK"
        const val EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL"
        const val EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE"
        const val EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2"
        const val EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH"
        const val EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT"
        const val EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS"
        const val EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
            "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER"
        const val EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE"
        const val EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC"
        const val EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC"
        const val EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE"
        const val EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC"
        const val EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE"
        const val EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC"
        const val EXTRA_NOAUDIOPROCESSING_ENABLED = "org.appspot.apprtc.NOAUDIOPROCESSING"
        const val EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP"
        const val EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED =
            "org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE"
        const val EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES"
        const val EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC"
        const val EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC"
        const val EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS"
        const val EXTRA_DISABLE_WEBRTC_AGC_AND_HPF =
            "org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL"
        const val EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD"
        const val EXTRA_TRACING = "org.appspot.apprtc.TRACING"
        const val EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE"
        const val EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME"
        const val EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA"
        const val EXTRA_SAVE_REMOTE_VIDEO_TO_FILE =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE"
        const val EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH"
        const val EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT"
        const val EXTRA_USE_VALUES_FROM_INTENT =
            "org.appspot.apprtc.USE_VALUES_FROM_INTENT"
        const val EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED"
        const val EXTRA_ORDERED = "org.appspot.apprtc.ORDERED"
        const val EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS"
        const val EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS"
        const val EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL"
        const val EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED"
        const val EXTRA_ID = "org.appspot.apprtc.ID"
        const val EXTRA_ENABLE_RTCEVENTLOG = "org.appspot.apprtc.ENABLE_RTCEVENTLOG"
        const val EXTRA_USE_LEGACY_AUDIO_DEVICE =
            "org.appspot.apprtc.USE_LEGACY_AUDIO_DEVICE"

         const val STAT_CALLBACK_PERIOD = 1000
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.button_call_disconnect->{
               disconnect()
            }
        }
    }
}
