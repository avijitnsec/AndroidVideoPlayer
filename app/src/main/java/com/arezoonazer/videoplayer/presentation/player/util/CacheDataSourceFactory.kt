package com.arezoonazer.videoplayer.presentation.player.util

import android.content.Context
import android.util.Log
import com.arezoonazer.videoplayer.R
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import java.io.File

class CacheDataSourceFactory(private val context: Context, private val maxCacheSize: Long, private val maxFileSize: Long) : DataSource.Factory {
    private val defaultDataSourceFactory: DataSource.Factory
    override fun createDataSource(): DataSource {
        val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
        simpleCache = getInstance(evictor)
        Log.d(TAG, "createDataSource() called" + context.cacheDir)
        return CacheDataSource(simpleCache, defaultDataSourceFactory.createDataSource(),
                FileDataSource(), CacheDataSink(simpleCache, maxFileSize),
                CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null)
    }

    fun getInstance(evictor: LeastRecentlyUsedCacheEvictor?): SimpleCache? {
        if (simpleCache == null) simpleCache = SimpleCache(File(context.cacheDir, "media"), evictor)
        return simpleCache
    }

    companion object {
        private const val TAG = "CacheDataSourceFactory"
        private var simpleCache: SimpleCache? = null
    }

    init {
        val userAgent = Util.getUserAgent(context, context.getString(R.string.app_name))
        val bandwidthMeter = DefaultBandwidthMeter()
        defaultDataSourceFactory = DefaultDataSourceFactory(context,
                bandwidthMeter,
                DefaultHttpDataSourceFactory(userAgent, bandwidthMeter))
    }
}