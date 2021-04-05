package com.arezoonazer.videoplayer.presentation.player.util

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.arezoonazer.videoplayer.data.database.Subtitle
import com.arezoonazer.videoplayer.data.model.VideoSource
import com.arezoonazer.videoplayer.data.model.VideoSource.SingleVideo
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.ContentType
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util

class VideoPlayer(private val playerView: PlayerView,
                  private val context: Context,
                  private val videoSource: VideoSource,
                  private val playerController: PlayerController) {
    private val CLASS_NAME = VideoPlayer::class.java.name
    var player: SimpleExoPlayer? = null
        private set
    private var mediaSource: MediaSource? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var widthOfScreen = 0
    var currentVideoIndex: Int
        private set
    private var componentListener: ComponentListener? = null
    private var cacheDataSourceFactory: CacheDataSourceFactory? = null
    var isLock = false
        private set

    /******************************************************************
     * initialize ExoPlayer
     */
    private fun initializePlayer() {
        playerView.requestFocus()
        componentListener = ComponentListener()
        cacheDataSourceFactory = CacheDataSourceFactory(
                context,
                100 * 1024 * 1024,
                5 * 1024 * 1024)
        trackSelector = DefaultTrackSelector(context)
        //        trackSelector.setParameters(trackSelector
//                .buildUponParameters());
        player = SimpleExoPlayer.Builder(context)
                .setTrackSelector(trackSelector!!)
                .build()
        playerView.player = player
        playerView.keepScreenOn = true
        player!!.playWhenReady = true
        player!!.addListener(componentListener!!)
        //build mediaSource depend on video type (Regular, HLS, DASH, etc)
        mediaSource = buildMediaSource(videoSource.videos!![currentVideoIndex], cacheDataSourceFactory)
        player!!.prepare(mediaSource!!)
        //resume video
        seekToSelectedPosition(videoSource.videos!![currentVideoIndex].watchedLength!!, false)
        if (videoSource.videos!!.size == 1 || isLastVideo) playerController.disableNextButtonOnLastVideo(true)
    }

    /******************************************************************
     * building mediaSource depend on stream type and caching
     */
    private fun buildMediaSource(singleVideo: SingleVideo, cacheDataSourceFactory: CacheDataSourceFactory?): MediaSource {
        val source = Uri.parse(singleVideo.url)
        @ContentType val type = Util.inferContentType(source)
        return when (type) {
            C.TYPE_SS -> {
                Log.d(TAG, "buildMediaSource() C.TYPE_SS = [" + C.TYPE_SS + "]")
                SsMediaSource.Factory(cacheDataSourceFactory!!).createMediaSource(source)
            }
            C.TYPE_DASH -> {
                Log.d(TAG, "buildMediaSource() C.TYPE_DASH = [" + C.TYPE_DASH + "]")
                DashMediaSource.Factory(cacheDataSourceFactory!!).createMediaSource(source)
            }
            C.TYPE_HLS -> {
                Log.d(TAG, "buildMediaSource() C.TYPE_HLS = [" + C.TYPE_HLS + "]")
                HlsMediaSource.Factory(cacheDataSourceFactory!!).createMediaSource(source)
            }
            C.TYPE_OTHER -> {
                Log.d(TAG, "buildMediaSource() C.TYPE_OTHER = [" + C.TYPE_OTHER + "]")
                ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(source)
            }
            else -> {
                throw IllegalStateException("Unsupported type: $source")
            }
        }
    }

    fun pausePlayer() {
        player!!.playWhenReady = false
    }

    fun resumePlayer() {
        player!!.playWhenReady = true
    }

    fun releasePlayer() {
        if (player == null) return
        playerController.setVideoWatchedLength()
        playerView.player = null
        player!!.release()
        player!!.removeListener(componentListener!!)
        player = null
    }

    val currentVideo: SingleVideo?
        get() = videoSource.videos!![currentVideoIndex]

    /************************************************************
     * mute, unMute
     */
    fun setMute(mute: Boolean) {
        val currentVolume = player!!.volume
        if (currentVolume > 0 && mute) {
            player!!.volume = 0f
            playerController.setMuteMode(true)
        } else if (!mute && currentVolume == 0f) {
            player!!.volume = 1f
            playerController.setMuteMode(false)
        }
    }

    /***********************************************************
     * manually select stream quality
     */
    fun setSelectedQuality(activity: Activity?) {
        val mappedTrackInfo: MappedTrackInfo?
        if (trackSelector != null) {
            mappedTrackInfo = trackSelector!!.currentMappedTrackInfo
            if (mappedTrackInfo != null) {
                val rendererIndex = 0 // renderer for video
                val rendererType = mappedTrackInfo.getRendererType(rendererIndex)
                val allowAdaptiveSelections = (rendererType == C.TRACK_TYPE_VIDEO
                        || (rendererType == C.TRACK_TYPE_AUDIO
                        && mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
                        == MappedTrackInfo.RENDERER_SUPPORT_NO_TRACKS))
                val dialogPair = MyTrackSelectionView.getDialog(activity, trackSelector,
                        rendererIndex,
                        player!!.videoFormat!!.bitrate.toLong())
                dialogPair.second.setShowDisableOption(false)
                dialogPair.second.setAllowAdaptiveSelections(allowAdaptiveSelections)
                dialogPair.second.animate()
                Log.d(TAG, "dialogPair.first.getListView()" + dialogPair.first.listView)
                dialogPair.first.show()
            }
        }
    }

    /***********************************************************
     * double tap event and seekTo
     */
    fun seekToSelectedPosition(hour: Int, minute: Int, second: Int) {
        val playbackPosition = ((hour * 3600 + minute * 60 + second) * 1000).toLong()
        player!!.seekTo(playbackPosition)
    }

    fun seekToSelectedPosition(millisecond: Long, rewind: Boolean) {
        if (rewind) {
            player!!.seekTo(player!!.currentPosition - 15000)
            return
        }
        player!!.seekTo(millisecond * 1000)
    }

    fun seekToOnDoubleTap() {
        getWidthOfScreen()
        val gestureDetector = GestureDetector(context,
                object : SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val positionOfDoubleTapX = e.x
                        if (positionOfDoubleTapX < widthOfScreen / 2) player!!.seekTo(player!!.currentPosition - 5000) else player!!.seekTo(player!!.currentPosition + 5000)
                        Log.d(TAG, "onDoubleTap(): widthOfScreen >> " + widthOfScreen +
                                " positionOfDoubleTapX >>" + positionOfDoubleTapX)
                        return true
                    }
                })
        playerView.setOnTouchListener { v: View?, event: MotionEvent? -> gestureDetector.onTouchEvent(event) }
    }

    private fun getWidthOfScreen() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        widthOfScreen = metrics.widthPixels
    }

    fun seekToNext() {
        if (currentVideoIndex < videoSource.videos!!.size - 1) {
            setCurrentVideoPosition()
            currentVideoIndex++
            mediaSource = buildMediaSource(videoSource.videos!![currentVideoIndex], cacheDataSourceFactory)
            player!!.prepare(mediaSource!!, true, true)
            if (videoSource.videos!![currentVideoIndex].watchedLength != null) seekToSelectedPosition(videoSource.videos!![currentVideoIndex].watchedLength!!, false)
            if (isLastVideo) playerController.disableNextButtonOnLastVideo(true)
        }
    }

    private val isLastVideo: Boolean
        private get() = currentVideoIndex == videoSource.videos!!.size - 1

    fun seekToPrevious() {
        playerController.disableNextButtonOnLastVideo(false)
        if (currentVideoIndex == 0) {
            seekToSelectedPosition(0, false)
            return
        }
        if (currentVideoIndex > 0) {
            setCurrentVideoPosition()
            currentVideoIndex--
            mediaSource = buildMediaSource(videoSource.videos!![currentVideoIndex], cacheDataSourceFactory)
            player!!.prepare(mediaSource!!, true, true)
            if (videoSource.videos!![currentVideoIndex].watchedLength != null) seekToSelectedPosition(videoSource.videos!![currentVideoIndex].watchedLength!!, false)
        }
    }

    private fun setCurrentVideoPosition() {
        if (currentVideo == null) return
        currentVideo!!.watchedLength = player!!.currentPosition / 1000 //second
    }

    //second
    val watchedLength: Long
        get() = if (currentVideo == null) 0 else player!!.currentPosition / 1000
    //second

    /***********************************************************
     * manually select subtitle
     */
    fun setSelectedSubtitle(subtitle: Subtitle) {
        if (TextUtils.isEmpty(subtitle.title)) Log.d(TAG, "setSelectedSubtitle: subtitle title is empty")
        val subtitleFormat = Format.createTextSampleFormat(
                null,
                MimeTypes.APPLICATION_SUBRIP,
                Format.NO_VALUE,
                null)

//        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context,
//                Util.getUserAgent(context,CLASS_NAME ));
        val subtitleSource: MediaSource = SingleSampleMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(Uri.parse(subtitle.subtitleUrl), subtitleFormat, C.TIME_UNSET)


        //optional
        playerController.changeSubtitleBackground()
        player!!.prepare(MergingMediaSource(mediaSource, subtitleSource), false, false)
        playerController.showSubtitle(true)
        resumePlayer()
    }

    /***********************************************************
     * playerView listener for lock and unlock screen
     */
    fun lockScreen(isLock: Boolean) {
        this.isLock = isLock
    }

    /***********************************************************
     * Listeners
     */
    private inner class ComponentListener : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            Log.d(TAG, "onPlayerStateChanged: playWhenReady: $playWhenReady playbackState: $playbackState")
            when (playbackState) {
                Player.STATE_IDLE -> {
                    playerController.showProgressBar(false)
                    playerController.showRetryBtn(true)
                }
                Player.STATE_BUFFERING -> playerController.showProgressBar(true)
                Player.STATE_READY -> {
                    playerController.showProgressBar(false)
                    playerController.audioFocus()
                }
                Player.STATE_ENDED -> {
                    playerController.showProgressBar(false)
                    playerController.videoEnded()
                }
                else -> {
                }
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            playerController.showProgressBar(false)
            playerController.showRetryBtn(true)
        }
    }

    companion object {
        private const val TAG = "VideoPlayer"
    }

    init {
        currentVideoIndex = videoSource.selectedSourceIndex
        initializePlayer()
    }
}