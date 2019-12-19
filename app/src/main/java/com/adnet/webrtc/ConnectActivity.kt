package com.adnet.webrtc

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.app.ActivityCompat.requestPermissions
import com.adnet.webrtc.CallActivity.Companion.EXTRA_AUDIOCODEC
import com.adnet.webrtc.CallActivity.Companion.EXTRA_AUDIO_BITRATE
import com.adnet.webrtc.CallActivity.Companion.EXTRA_CMDLINE
import com.adnet.webrtc.CallActivity.Companion.EXTRA_DATA_CHANNEL_ENABLED
import com.adnet.webrtc.CallActivity.Companion.EXTRA_DISABLE_BUILT_IN_AEC
import com.adnet.webrtc.CallActivity.Companion.EXTRA_DISABLE_BUILT_IN_AGC
import com.adnet.webrtc.CallActivity.Companion.EXTRA_DISABLE_BUILT_IN_NS
import com.adnet.webrtc.CallActivity.Companion.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF
import com.adnet.webrtc.CallActivity.Companion.EXTRA_DISPLAY_HUD
import com.adnet.webrtc.CallActivity.Companion.EXTRA_ENABLE_RTCEVENTLOG
import com.adnet.webrtc.CallActivity.Companion.EXTRA_OPENSLES_ENABLED
import com.adnet.webrtc.CallActivity.Companion.EXTRA_RUNTIME
import com.adnet.webrtc.CallActivity.Companion.EXTRA_TRACING
import com.adnet.webrtc.CallActivity.Companion.EXTRA_USE_LEGACY_AUDIO_DEVICE
import kotlinx.android.synthetic.main.activity_connect.*
import org.json.JSONArray
import org.json.JSONException
import java.util.*

@Suppress("DEPRECATION", "DEPRECATED_IDENTITY_EQUALS", "SameParameterValue")
class ConnectActivity : AppCompatActivity(), View.OnClickListener {

    private val PERMISSIONS_START_CALL = arrayOf(
        Manifest.permission.RECORD_AUDIO
    ) //WRITE_EXTERNAL_STORAGE, CAPTURE_VIDEO_OUTPUT

    private val PERMISSIONS_REQUEST_START_CALL = 101

    private var startCallIntent: Intent? = null

    private val roomEditText: EditText? = null
    private lateinit var sharedPref: SharedPreferences
    private var keyprefResolution: String? = null
    private var keyprefFps: String? = null
    private var keyprefVideoBitrateType: String? = null
    private var keyprefVideoBitrateValue: String? = null
    private var keyprefAudioBitrateType: String? = null
    private var keyprefAudioBitrateValue: String? = null
    private var keyprefRoomServerUrl: String? = null
    private var keyprefRoom: String? = null
    private var keyprefRoomList: String? = null
    private lateinit var roomList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getPrefercenManager()
        setContentView(R.layout.activity_connect)
        intent = Intent(this, CallActivity::class.java)

        initView()

    }

    // Get setting keys.
    private fun getPrefercenManager() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        keyprefResolution = getString(R.string.pref_resolution_key)
        keyprefFps = getString(R.string.pref_fps_key)
        keyprefVideoBitrateType = getString(R.string.pref_maxvideobitrate_key)
        keyprefVideoBitrateValue =
            getString(R.string.pref_maxvideobitratevalue_key)
        keyprefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key)
        keyprefAudioBitrateValue =
            getString(R.string.pref_startaudiobitratevalue_key)
        keyprefRoomServerUrl = getString(R.string.pref_room_server_url_key)
        keyprefRoom = getString(R.string.pref_room_key)
        keyprefRoomList = getString(R.string.pref_room_list_key)

    }

    private fun initView() {
        room_edittext.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    return true
                }
                return false
            }

        })
        roomEditText?.requestFocus()

        connect_button.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        val room = sharedPref.getString(keyprefRoom, "")
        roomEditText?.setText(room)
        roomList = ArrayList()
        val roomListJson = sharedPref.getString(keyprefRoomList, null)
        if (roomListJson != null) {
            try {
                val jsonArray = JSONArray(roomListJson)
                for (i in 0 until jsonArray.length()) {
                    roomList.add(jsonArray[i].toString())
                }
            } catch (e: JSONException) {
                //  Log.e(ConnectActivity.TAG, "Failed to load room list: $e")
            }
        }

    }

    override fun onPause() {
        super.onPause()

        val room = roomEditText?.text.toString()
        val roomListJson = JSONArray(roomList).toString()
        val editor = sharedPref.edit()
        editor.putString(keyprefRoom, room)
        editor.putString(keyprefRoomList, roomListJson)
        editor.commit()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONNECTION_REQUEST && commandLineRun) {
            Log.d(TAG, "Return: $resultCode")
            setResult(resultCode)
            commandLineRun = false
            finish()
        }
    }

    private fun validateUrl(url: String?): Boolean {
        if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
            return true
        }
        AlertDialog.Builder(this)
            .setTitle(getText(R.string.invalid_url_title))
            .setMessage(getString(R.string.invalid_url_text, url))
            .setCancelable(false)
            .setNeutralButton(R.string.ok,
                DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
            .create()
            .show()
        return false
    }

    private fun connectToRoom(
        roomId: String, commandLineRun: Boolean, loopback: Boolean,
        useValuesFromIntent: Boolean, runTimeMs: Int
    ) {
        val intent = Intent(this, CallActivity::class.java)
        var roomId = roomId
        ConnectActivity.commandLineRun = commandLineRun
        // roomId is random for loopback.
        if (loopback) {
            roomId = Random().nextInt(100000000).toString()
        }
        Log.d("Hien",roomId.toString())
        val roomUrl = sharedPref.getString(
            keyprefRoomServerUrl,
            getString(R.string.pref_room_server_url_default)
        )

        // Video call enabled flag.
        val videoCallEnabled: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_videocall_key,
            CallActivity.EXTRA_VIDEO_CALL,
            R.string.pref_videocall_default,
            useValuesFromIntent
        )
        // Use screencapture option.
        val useScreencapture: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_screencapture_key,
            CallActivity.EXTRA_SCREENCAPTURE,
            R.string.pref_screencapture_default,
            useValuesFromIntent
        )
        // Use Camera2 option.
        val useCamera2: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_camera2_key, CallActivity.EXTRA_CAMERA2,
            R.string.pref_camera2_default, useValuesFromIntent
        )
        // Get default codecs.
        val videoCodec: String = sharedPrefGetString(
            intent, this, sharedPref,
            R.string.pref_videocodec_key,
            CallActivity.EXTRA_VIDEOCODEC,
            R.string.pref_videocodec_default,
            useValuesFromIntent
        )
        val audioCodec: String = sharedPrefGetString(
            intent, this, sharedPref,
            R.string.pref_audiocodec_key,
            CallActivity.EXTRA_AUDIOCODEC,
            R.string.pref_audiocodec_default,
            useValuesFromIntent
        )
        // Check HW codec flag.
        val hwCodec: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_hwcodec_key,
            CallActivity.EXTRA_HWCODEC_ENABLED,
            R.string.pref_hwcodec_default,
            useValuesFromIntent
        )
        // Check Capture to texture.
        val captureToTexture: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_capturetotexture_key,
            CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED,
            R.string.pref_capturetotexture_default,
            useValuesFromIntent
        )
        // Check FlexFEC.
        val flexfecEnabled: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_flexfec_key,
            CallActivity.EXTRA_FLEXFEC_ENABLED,
            R.string.pref_flexfec_default,
            useValuesFromIntent
        )
        // Check Disable Audio Processing flag.
        val noAudioProcessing: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_noaudioprocessing_key,
            CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED,
            R.string.pref_noaudioprocessing_default,
            useValuesFromIntent
        )
        val aecDump: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_aecdump_key,
            CallActivity.EXTRA_AECDUMP_ENABLED,
            R.string.pref_aecdump_default,
            useValuesFromIntent
        )
        val saveInputAudioToFile: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_enable_save_input_audio_to_file_key,
            CallActivity.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED,
            R.string.pref_enable_save_input_audio_to_file_default,
            useValuesFromIntent
        )
        // Check OpenSL ES enabled flag.
        val useOpenSLES: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_opensles_key,
            CallActivity.EXTRA_OPENSLES_ENABLED,
            R.string.pref_opensles_default,
            useValuesFromIntent
        )
        // Check Disable built-in AEC flag.
        val disableBuiltInAEC: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_disable_built_in_aec_key,
            CallActivity.EXTRA_DISABLE_BUILT_IN_AEC,
            R.string.pref_disable_built_in_aec_default,
            useValuesFromIntent
        )
        // Check Disable built-in AGC flag.
        val disableBuiltInAGC: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_disable_built_in_agc_key,
            CallActivity.EXTRA_DISABLE_BUILT_IN_AGC,
            R.string.pref_disable_built_in_agc_default,
            useValuesFromIntent
        )
        // Check Disable built-in NS flag.
        val disableBuiltInNS: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_disable_built_in_ns_key,
            CallActivity.EXTRA_DISABLE_BUILT_IN_NS,
            R.string.pref_disable_built_in_ns_default,
            useValuesFromIntent
        )
        // Check Disable gain control
        val disableWebRtcAGCAndHPF: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_disable_webrtc_agc_and_hpf_key,
            CallActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF,
            R.string.pref_disable_webrtc_agc_and_hpf_key,
            useValuesFromIntent
        )
        // Get video resolution from settings.
        var videoWidth = 0
        var videoHeight = 0
        if (useValuesFromIntent) {
            videoWidth = intent.getIntExtra(CallActivity.EXTRA_VIDEO_WIDTH, 0)
            videoHeight = intent.getIntExtra(CallActivity.EXTRA_VIDEO_HEIGHT, 0)
        }
        if (videoWidth == 0 && videoHeight == 0) {
            val resolution = sharedPref.getString(
                keyprefResolution,
                getString(R.string.pref_resolution_default)
            )
            val dimensions = resolution!!.split("[ x]+").toTypedArray()
            if (dimensions.size == 2) {
                try {
                    videoWidth = dimensions[0].toInt()
                    videoHeight = dimensions[1].toInt()
                } catch (e: NumberFormatException) {
                    videoWidth = 0
                    videoHeight = 0
                    Log.e(
                        TAG,
                        "Wrong video resolution setting: $resolution"
                    )
                }
            }
        }
        // Get camera fps from settings.
        var cameraFps = 0
        if (useValuesFromIntent) {
            cameraFps = intent.getIntExtra(CallActivity.EXTRA_VIDEO_FPS, 0)
        }
        if (cameraFps == 0) {
            val fps = sharedPref.getString(
                keyprefFps,
                getString(R.string.pref_fps_default)
            )
            val fpsValues = fps!!.split("[ x]+").toTypedArray()
            if (fpsValues.size == 2) {
                try {
                    cameraFps = fpsValues[0].toInt()
                } catch (e: NumberFormatException) {
                    cameraFps = 0
                    Log.e(TAG, "Wrong camera fps setting: $fps")
                }
            }
        }
        // Check capture quality slider flag.
        val captureQualitySlider: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_capturequalityslider_key,
            CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
            R.string.pref_capturequalityslider_default, useValuesFromIntent
        )
        // Get video and audio start bitrate.
        var videoStartBitrate = 0
        if (useValuesFromIntent) {
            videoStartBitrate = intent.getIntExtra(CallActivity.EXTRA_VIDEO_BITRATE, 0)
        }
        if (videoStartBitrate == 0) {
            val bitrateTypeDefault =
                getString(R.string.pref_maxvideobitrate_default)
            val bitrateType =
                sharedPref.getString(keyprefVideoBitrateType, bitrateTypeDefault)
            if (bitrateType != bitrateTypeDefault) {
                val bitrateValue = sharedPref.getString(
                    keyprefVideoBitrateValue,
                    getString(R.string.pref_maxvideobitratevalue_default)
                )
                videoStartBitrate = bitrateValue!!.toInt()
            }
        }
        var audioStartBitrate = 0
        if (useValuesFromIntent) {
            audioStartBitrate = intent.getIntExtra(CallActivity.EXTRA_AUDIO_BITRATE, 0)
        }
        if (audioStartBitrate == 0) {
            val bitrateTypeDefault =
                getString(R.string.pref_startaudiobitrate_default)
            val bitrateType =
                sharedPref.getString(keyprefAudioBitrateType, bitrateTypeDefault)
            if (bitrateType != bitrateTypeDefault) {
                val bitrateValue = sharedPref.getString(
                    keyprefAudioBitrateValue,
                    getString(R.string.pref_startaudiobitratevalue_default)
                )
                audioStartBitrate = bitrateValue!!.toInt()
            }
        }
        // Check statistics display option.
        val displayHud: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_displayhud_key,
            CallActivity.EXTRA_DISPLAY_HUD,
            R.string.pref_displayhud_default,
            useValuesFromIntent
        )
        val tracing: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_tracing_key, CallActivity.EXTRA_TRACING,
            R.string.pref_tracing_default, useValuesFromIntent
        )
        // Check Enable RtcEventLog.
        val rtcEventLogEnabled: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_enable_rtceventlog_key,
            CallActivity.EXTRA_ENABLE_RTCEVENTLOG,
            R.string.pref_enable_rtceventlog_default,
            useValuesFromIntent
        )
        val useLegacyAudioDevice: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_use_legacy_audio_device_key,
            CallActivity.EXTRA_USE_LEGACY_AUDIO_DEVICE,
            R.string.pref_use_legacy_audio_device_default,
            useValuesFromIntent
        )
        // Get datachannel options
        val dataChannelEnabled: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_enable_datachannel_key,
            CallActivity.EXTRA_DATA_CHANNEL_ENABLED,
            R.string.pref_enable_datachannel_default,
            useValuesFromIntent
        )
        val ordered: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_ordered_key, CallActivity.EXTRA_ORDERED,
            R.string.pref_ordered_default, useValuesFromIntent
        )
        val negotiated: Boolean = sharedPrefGetBoolean(
            intent, this, sharedPref,
            R.string.pref_negotiated_key,
            CallActivity.EXTRA_NEGOTIATED,
            R.string.pref_negotiated_default,
            useValuesFromIntent
        )
        val maxRetrMs: Int = sharedPrefGetInteger(
            intent, this, sharedPref,
            R.string.pref_max_retransmit_time_ms_key,
            CallActivity.EXTRA_MAX_RETRANSMITS_MS,
            R.string.pref_max_retransmit_time_ms_default,
            useValuesFromIntent
        )
        val maxRetr: Int = sharedPrefGetInteger(
            intent, this, sharedPref,
            R.string.pref_max_retransmits_key,
            CallActivity.EXTRA_MAX_RETRANSMITS,
            R.string.pref_max_retransmits_default,
            useValuesFromIntent
        )
        val id: Int = sharedPrefGetInteger(
            intent, this, sharedPref,
            R.string.pref_data_id_key, CallActivity.EXTRA_ID,
            R.string.pref_data_id_default, useValuesFromIntent
        )
        val protocol: String = sharedPrefGetString(
            intent, this, sharedPref,
            R.string.pref_data_protocol_key,
            CallActivity.EXTRA_PROTOCOL,
            R.string.pref_data_protocol_default,
            useValuesFromIntent
        )
        // Start AppRTCMobile activity.
        Log.d(
            TAG,
            "Connecting to room $roomId at URL $roomUrl"
        )
        if (validateUrl(roomUrl)) {
            val uri = Uri.parse(roomUrl)

            intent.data = uri
            Log.d("Hien",roomId)
            intent.putExtra(CallActivity.EXTRA_ROOMID, roomId)
            intent.putExtra(CallActivity.EXTRA_LOOPBACK, loopback)
            intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, false)
            intent.putExtra(CallActivity.EXTRA_SCREENCAPTURE, useScreencapture)
            intent.putExtra(CallActivity.EXTRA_CAMERA2, useCamera2)
            intent.putExtra(CallActivity.EXTRA_VIDEO_WIDTH, videoWidth)
            intent.putExtra(CallActivity.EXTRA_VIDEO_HEIGHT, videoHeight)
            intent.putExtra(CallActivity.EXTRA_VIDEO_FPS, cameraFps)
            intent.putExtra(
                CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
                captureQualitySlider
            )
            intent.putExtra(CallActivity.EXTRA_VIDEO_BITRATE, videoStartBitrate)
            intent.putExtra(CallActivity.EXTRA_VIDEOCODEC, videoCodec)
            intent.putExtra(CallActivity.EXTRA_HWCODEC_ENABLED, hwCodec)
            intent.putExtra(CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, captureToTexture)
            intent.putExtra(CallActivity.EXTRA_FLEXFEC_ENABLED, flexfecEnabled)
            intent.putExtra(CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, noAudioProcessing)
            intent.putExtra(CallActivity.EXTRA_AECDUMP_ENABLED, aecDump)
            intent.putExtra(
                CallActivity.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED,
                saveInputAudioToFile
            )
            intent.putExtra(EXTRA_OPENSLES_ENABLED, useOpenSLES)
            intent.putExtra(EXTRA_DISABLE_BUILT_IN_AEC, disableBuiltInAEC)
            intent.putExtra(EXTRA_DISABLE_BUILT_IN_AGC, disableBuiltInAGC)
            intent.putExtra(EXTRA_DISABLE_BUILT_IN_NS, disableBuiltInNS)
            intent.putExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, disableWebRtcAGCAndHPF)
            intent.putExtra(EXTRA_AUDIO_BITRATE, audioStartBitrate)
            intent.putExtra(EXTRA_AUDIOCODEC, audioCodec)
            intent.putExtra(EXTRA_DISPLAY_HUD, displayHud)
            intent.putExtra(EXTRA_TRACING, tracing)
            intent.putExtra(EXTRA_ENABLE_RTCEVENTLOG, rtcEventLogEnabled)
            intent.putExtra(EXTRA_CMDLINE, commandLineRun)
            intent.putExtra(EXTRA_RUNTIME, runTimeMs)
            intent.putExtra(EXTRA_USE_LEGACY_AUDIO_DEVICE, useLegacyAudioDevice)
            intent.putExtra(EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled)
            if (dataChannelEnabled) {
                intent.putExtra(CallActivity.EXTRA_ORDERED, ordered)
                intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS_MS, maxRetrMs)
                intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS, maxRetr)
                intent.putExtra(CallActivity.EXTRA_PROTOCOL, protocol)
                intent.putExtra(CallActivity.EXTRA_NEGOTIATED, negotiated)
                intent.putExtra(CallActivity.EXTRA_ID, id)
            }
            if (useValuesFromIntent) {
                if (getIntent().hasExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA)) {
                    val videoFileAsCamera =
                        getIntent().getStringExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA)
                    intent.putExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA, videoFileAsCamera)
                }
                if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)) {
                    val saveRemoteVideoToFile =
                        getIntent().getStringExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)
                    intent.putExtra(
                        CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE,
                        saveRemoteVideoToFile
                    )
                }
                if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH)) {
                    val videoOutWidth = getIntent().getIntExtra(
                        CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH,
                        0
                    )
                    intent.putExtra(
                        CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH,
                        videoOutWidth
                    )
                }
                if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT)) {
                    val videoOutHeight = getIntent().getIntExtra(
                        CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT,
                        0
                    )
                    intent.putExtra(
                        CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT,
                        videoOutHeight
                    )
                }
            }
            startCallActivity(intent)
        }
    }

    private fun startCallActivity(intent: Intent) {
//        if (!hasPermissions(this, PERMISSIONS_START_CALL)) {
//            startCallIntent = intent
//            requestPermissions(
//                this,
//                PERMISSIONS_START_CALL,
//                PERMISSIONS_REQUEST_START_CALL
//            )
//            return
//        }
        startActivityForResult(intent, CONNECTION_REQUEST)
    }

    private fun hasPermissions(
        context: Context?,
        vararg permissions: Array<String>
    ): Boolean {
        if (context != null) {
            for (permission in permissions) {
                if (checkSelfPermission(
                        context,
                        permission.toString()
                    ) !== PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_REQUEST_START_CALL -> {
                if (hasPermissions(
                        this,
                        PERMISSIONS_START_CALL
                    )
                ) { // permission was granted, yay!
                    if (startCallIntent != null) startActivityForResult(
                        startCallIntent,
                        CONNECTION_REQUEST
                    )
                } else {
                    Toast.makeText(this, "Required permissions denied.", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

    companion object {
        private const val TAG = "ConnectActivity"
        private const val CONNECTION_REQUEST = 1
        private const val REMOVE_FAVORITE_INDEX = 0
        private var commandLineRun = false
    }

    override fun onClick(v: View) {
        when(v.id){
            R.id.connect_button->{
                connectToRoom(roomEditText?.text.toString(),
                    commandLineRun = false,
                    loopback = false,
                    useValuesFromIntent = false,
                    runTimeMs = 0
                )
            }
        }
    }

}
