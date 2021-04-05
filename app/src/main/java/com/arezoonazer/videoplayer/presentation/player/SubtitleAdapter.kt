package com.arezoonazer.videoplayer.presentation.player

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.arezoonazer.videoplayer.R
import com.arezoonazer.videoplayer.data.database.Subtitle
import com.arezoonazer.videoplayer.presentation.player.SubtitleAdapter.SubtitleViewHolder
import com.arezoonazer.videoplayer.presentation.player.util.VideoPlayer

class SubtitleAdapter(private val subtitleUrlList: List<Subtitle>, private val player: VideoPlayer) : RecyclerView.Adapter<SubtitleViewHolder>() {
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SubtitleViewHolder {
        return SubtitleViewHolder(LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.subtitle_item, viewGroup, false))
    }

    override fun onBindViewHolder(subtitleViewHolder: SubtitleViewHolder, i: Int) {
        subtitleViewHolder.onBind(subtitleUrlList[i])
    }

    override fun getItemCount(): Int {
        return subtitleUrlList.size
    }

    inner class SubtitleViewHolder(itemView: View) : ViewHolder(itemView) {
        var subtitleName: TextView
        fun onBind(subtitleUrl: Subtitle) {
            subtitleName.text = subtitleUrl.title
            Log.d("title", "subtitleUrl.getTitle() >> " + subtitleUrl.title)
            itemView.setOnClickListener { view: View? -> player.setSelectedSubtitle(subtitleUrl) }
        }

        init {
            subtitleName = itemView.findViewById(R.id.subtitle_text_view)
        }
    }
}