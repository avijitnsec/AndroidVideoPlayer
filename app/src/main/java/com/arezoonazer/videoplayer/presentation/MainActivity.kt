package com.arezoonazer.videoplayer.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.arezoonazer.videoplayer.R
import com.arezoonazer.videoplayer.data.database.AppDatabase
import com.arezoonazer.videoplayer.data.database.AppDatabase.Companion.getDatabase
import com.arezoonazer.videoplayer.data.database.Subtitle
import com.arezoonazer.videoplayer.data.database.Video
import com.arezoonazer.videoplayer.data.model.VideoSource
import com.arezoonazer.videoplayer.data.model.VideoSource.SingleVideo
import com.arezoonazer.videoplayer.presentation.player.PlayerActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private var database: AppDatabase? = null
    private val subtitleList: MutableList<Subtitle> = ArrayList()
    private var playMp4: TextView? = null
    private var playM3u8: TextView? = null
    private val videoUriList: MutableList<Video> = ArrayList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setLayout()
        initializeDb()
        makeListOfUri()
    }

    public override fun onDestroy() {
        super.onDestroy()
        database = null
    }

    private fun setLayout() {
        playMp4 = findViewById(R.id.mp4)
        playM3u8 = findViewById(R.id.m3u8)
        playMp4!!.setOnClickListener(View.OnClickListener { goToPlayerActivity(makeVideoSource(videoUriList, 0)) })
        playM3u8!!.setOnClickListener(View.OnClickListener { goToPlayerActivity(makeVideoSource(videoUriList, 1)) })
    }

    private fun initializeDb() {
        database = getDatabase(applicationContext)
    }

    private fun makeListOfUri() {
        videoUriList.add(Video("https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4", java.lang.Long.getLong("zero", 0)))
        videoUriList.add(Video("https://5b44cf20b0388.streamlock.net:8443/vod/smil:bbb.smil/playlist.m3u8", java.lang.Long.getLong("zero", 0)))
        subtitleList.add(Subtitle(2, "German", "https://durian.blender.org/wp-content/content/subtitles/sintel_en.srt"))
        subtitleList.add(Subtitle(2, "French", "https://durian.blender.org/wp-content/content/subtitles/sintel_fr.srt"))
        if (database!!.videoDao().allUrls.isEmpty()) {
            database!!.videoDao().insertAllVideoUrl(videoUriList)
            database!!.videoDao().insertAllSubtitleUrl(subtitleList)
        }
    }

    private fun makeVideoSource(videos: List<Video>, index: Int): VideoSource {
        setVideosWatchLength()
        val singleVideos: MutableList<SingleVideo> = ArrayList()
        for (i in videos.indices) {
            singleVideos.add(i, SingleVideo(
                    videos[i].videoUrl,
                    database!!.videoDao().getAllSubtitles(i + 1),
                    videos[i].watchedLength)
            )

        }
        return VideoSource(singleVideos, index)
    }

    private fun setVideosWatchLength(): List<Video> {
        val videosInDb = database!!.videoDao().videos
        for (i in videosInDb.indices) {
            videoUriList[i].watchedLength = videosInDb[i].watchedLength
        }
        return videoUriList
    }

    //start player for result due to future features
    private fun goToPlayerActivity(videoSource: VideoSource?) {
        val REQUEST_CODE = 1000
        val intent = Intent(applicationContext, PlayerActivity::class.java)
        intent.putExtra("videoSource", videoSource)

        startActivityForResult(intent, REQUEST_CODE)
    }
}