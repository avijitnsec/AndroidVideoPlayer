package com.arezoonazer.videoplayer.presentation.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.arezoonazer.videoplayer.R
import com.arezoonazer.videoplayer.app.PlayerApplication
import com.arezoonazer.videoplayer.data.database.AppDatabase.Companion.getDatabase
import com.arezoonazer.videoplayer.data.model.VideoSource
import com.arezoonazer.videoplayer.presentation.player.util.PlayerController
import com.arezoonazer.videoplayer.presentation.player.util.VideoPlayer
import com.google.android.exoplayer2.text.CaptionStyleCompat
import com.google.android.exoplayer2.ui.PlayerView

class PlayerActivity : AppCompatActivity(), View.OnClickListener, PlayerController {
    private var playerView: PlayerView? = null
    private var player: VideoPlayer? = null
    private var mute: ImageButton? = null
    private var unMute: ImageButton? = null
    private var subtitle: ImageButton? = null
    private var setting: ImageButton? = null
    private var lock: ImageButton? = null
    private var unLock: ImageButton? = null
    private var nextBtn: ImageButton? = null
    private var preBtn: ImageButton? = null
    private var retry: ImageButton? = null
    private var back: ImageButton? = null
    private var progressBar: ProgressBar? = null
    private var alertDialog: AlertDialog? = null
    private var videoSource: VideoSource? = null
    private var mAudioManager: AudioManager? = null
    private var disableBackPress = false

    /***********************************************************
     * Handle audio on different events
     */
    private val mOnAudioFocusChangeListener = OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (player != null) //  player.getPlayer().setPlayWhenReady(true);
                    break
                // Audio focus was lost, but it's possible to duck (i.e.: play quietly)
                if (player != null) player.getPlayer().setPlayWhenReady(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (player != null) player.getPlayer().setPlayWhenReady(false)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS ->                             // Lost audio focus, probably "permanently"
                // Lost audio focus, but will gain it back (shortly), so note whether
                // playback should resume
                if (player != null) player.getPlayer().setPlayWhenReady(false)
        }
    }

    /***********************************************************
     * Activity lifecycle
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        dataFromIntent
        setupLayout()
        initSource()
    }

    private val dataFromIntent: Unit
        private get() {
            videoSource = intent.getParcelableExtra("videoSource")
        }

    private fun setupLayout() {
        playerView = findViewById(R.id.demo_player_view)
        progressBar = findViewById(R.id.progress_bar)
        mute = findViewById(R.id.btn_mute)
        unMute = findViewById(R.id.btn_unMute)
        subtitle = findViewById(R.id.btn_subtitle)
        setting = findViewById(R.id.btn_settings)
        lock = findViewById(R.id.btn_lock)
        unLock = findViewById(R.id.btn_unLock)
        nextBtn = findViewById(R.id.btn_next)
        preBtn = findViewById(R.id.btn_prev)
        retry = findViewById(R.id.retry_btn)
        back = findViewById(R.id.btn_back)

        //optional setting
        playerView.getSubtitleView()!!.visibility = View.GONE
        mute.setOnClickListener(this)
        unMute.setOnClickListener(this)
        subtitle.setOnClickListener(this)
        setting.setOnClickListener(this)
        lock.setOnClickListener(this)
        unLock.setOnClickListener(this)
        nextBtn.setOnClickListener(this)
        preBtn.setOnClickListener(this)
        retry.setOnClickListener(this)
        back.setOnClickListener(this)
    }

    private fun initSource() {
        if (videoSource!!.videos == null) {
            Toast.makeText(this, "can not play video", Toast.LENGTH_SHORT).show()
            return
        }
        player = VideoPlayer(playerView!!, applicationContext, videoSource!!, this)
        checkIfVideoHasSubtitle()
        mAudioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager

        //optional setting
        playerView!!.subtitleView!!.visibility = View.GONE
        player!!.seekToOnDoubleTap()
        playerView!!.setControllerVisibilityListener { visibility: Int ->
            Log.i(TAG, "onVisibilityChange: $visibility")
            if (player!!.isLock()) playerView!!.hideController()
            back!!.visibility = if (visibility == View.VISIBLE && !player!!.isLock()) View.VISIBLE else View.GONE
        }
    }

    public override fun onStart() {
        super.onStart()
        if (player != null) player!!.resumePlayer()
    }

    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (player != null) player!!.resumePlayer()
    }

    public override fun onPause() {
        super.onPause()
        if (player != null) player!!.releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mAudioManager != null) {
            mAudioManager!!.abandonAudioFocus(mOnAudioFocusChangeListener)
            mAudioManager = null
        }
        if (player != null) {
            player!!.releasePlayer()
            player = null
        }
        PlayerApplication.getRefWatcher(this)!!.watch(this)
    }

    override fun onBackPressed() {
        if (disableBackPress) return
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hideSystemUi()
    }

    override fun onClick(view: View) {
        val controllerId = view.id
        when (controllerId) {
            R.id.btn_mute -> player!!.setMute(true)
            R.id.btn_unMute -> player!!.setMute(false)
            R.id.btn_settings -> player!!.setSelectedQuality(this)
            R.id.btn_subtitle -> prepareSubtitles()
            R.id.btn_lock -> updateLockMode(true)
            R.id.btn_unLock -> updateLockMode(false)
            R.id.exo_rew -> player!!.seekToSelectedPosition(0, true)
            R.id.btn_back -> onBackPressed()
            R.id.retry_btn -> {
                initSource()
                showProgressBar(true)
                showRetryBtn(false)
            }
            R.id.btn_next -> {
                player!!.seekToNext()
                checkIfVideoHasSubtitle()
            }
            R.id.btn_prev -> {
                player!!.seekToPrevious()
                checkIfVideoHasSubtitle()
            }
            else -> {
            }
        }
    }

    /***********************************************************
     * UI config
     */
    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        playerView!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun showSubtitle(show: Boolean) {
        if (player == null || playerView!!.subtitleView == null) return
        if (!show) {
            playerView!!.subtitleView!!.visibility = View.GONE
            return
        }
        alertDialog!!.dismiss()
        playerView!!.subtitleView!!.visibility = View.VISIBLE
    }

    override fun changeSubtitleBackground() {
        val captionStyleCompat = CaptionStyleCompat(Color.YELLOW, Color.TRANSPARENT, Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.LTGRAY, null)
        playerView!!.subtitleView!!.setStyle(captionStyleCompat)
    }

    private fun checkIfVideoHasSubtitle(): Boolean {
        if (player.getCurrentVideo().subtitles == null ||
                player.getCurrentVideo().subtitles.size == 0) {
            subtitle!!.setImageResource(R.drawable.exo_no_subtitle_btn)
            return true
        }
        subtitle!!.setImageResource(R.drawable.exo_subtitle_btn)
        return false
    }

    private fun prepareSubtitles() {
        if (player == null || playerView!!.subtitleView == null) return
        if (checkIfVideoHasSubtitle()) {
            Toast.makeText(this, getString(R.string.no_subtitle), Toast.LENGTH_SHORT).show()
            return
        }
        player!!.pausePlayer()
        showSubtitleDialog()
    }

    private fun showSubtitleDialog() {
        //init subtitle dialog
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val builder = AlertDialog.Builder(this, R.style.MyDialogTheme)
        val inflater = LayoutInflater.from(applicationContext)
        val view = inflater.inflate(R.layout.subtitle_selection_dialog, null)
        builder.setView(view)
        alertDialog = builder.create()

        // set the height and width of dialog
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(alertDialog!!.window!!.attributes)
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.CENTER
        alertDialog!!.window!!.attributes = layoutParams
        val recyclerView: RecyclerView = view.findViewById(R.id.subtitle_recycler_view)
        recyclerView.adapter = SubtitleAdapter(player.getCurrentVideo().subtitles, player)
        for (i in player.getCurrentVideo().subtitles.indices) {
            Log.d("subtitle", "showSubtitleDialog: " + player.getCurrentVideo().subtitles.get(i).title)
        }
        val noSubtitle = view.findViewById<TextView>(R.id.no_subtitle_text_view)
        noSubtitle.setOnClickListener { view1: View? ->
            if (playerView!!.subtitleView!!.visibility == View.VISIBLE) showSubtitle(false)
            alertDialog!!.dismiss()
            player!!.resumePlayer()
        }
        val cancelDialog = view.findViewById<Button>(R.id.cancel_dialog_btn)
        cancelDialog.setOnClickListener { view1: View? ->
            alertDialog!!.dismiss()
            player!!.resumePlayer()
        }

        // to prevent dialog box from getting dismissed on outside touch
        alertDialog!!.setCanceledOnTouchOutside(false)
        alertDialog!!.show()
    }

    override fun setMuteMode(mute: Boolean) {
        if (player != null && playerView != null) {
            if (mute) {
                this.mute!!.visibility = View.GONE
                unMute!!.visibility = View.VISIBLE
            } else {
                unMute!!.visibility = View.GONE
                this.mute!!.visibility = View.VISIBLE
            }
        }
    }

    override fun showProgressBar(visible: Boolean) {
        progressBar!!.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateLockMode(isLock: Boolean) {
        if (player == null || playerView == null) return
        player!!.lockScreen(isLock)
        if (isLock) {
            disableBackPress = true
            playerView!!.hideController()
            unLock!!.visibility = View.VISIBLE
            return
        }
        disableBackPress = false
        playerView!!.showController()
        unLock!!.visibility = View.GONE
    }

    override fun showRetryBtn(visible: Boolean) {
        retry!!.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun audioFocus() {
        mAudioManager!!.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
    }

    override fun setVideoWatchedLength() {
        getDatabase(applicationContext).videoDao().updateWatchedLength(player.getCurrentVideo().url, player.getWatchedLength())
    }

    override fun videoEnded() {
        getDatabase(applicationContext).videoDao().updateWatchedLength(player.getCurrentVideo().url, 0)
        player!!.seekToNext()
    }

    override fun disableNextButtonOnLastVideo(disable: Boolean) {
        if (disable) {
            nextBtn!!.setImageResource(R.drawable.exo_disable_next_btn)
            nextBtn!!.isEnabled = false
            return
        }
        nextBtn!!.setImageResource(R.drawable.exo_next_btn)
        nextBtn!!.isEnabled = true
    }

    companion object {
        private const val TAG = "PlayerActivity"
    }
}