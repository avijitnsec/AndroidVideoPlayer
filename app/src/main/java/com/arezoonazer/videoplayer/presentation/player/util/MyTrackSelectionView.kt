package com.arezoonazer.videoplayer.presentation.player.util
/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//package com.google.android.exoplayer2.ui;
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Html
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckedTextView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import com.arezoonazer.videoplayer.R
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.TrackNameProvider
import com.google.android.exoplayer2.util.Assertions
import java.util.*

/**
 * A view for making track selections.
 */
class MyTrackSelectionView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val selectableItemBackgroundResourceId: Int
    private val inflater: LayoutInflater
    private val disableView: CheckedTextView
    private val defaultView: CheckedTextView
    private val componentListener: ComponentListener
    private var allowAdaptiveSelections = false
    private var trackNameProvider: TrackNameProvider
    private lateinit var trackViews: Array<Array<CheckedTextView?>>
    private var trackSelector: DefaultTrackSelector? = null
    private var rendererIndex = 0
    private var trackGroups: TrackGroupArray? = null
    private var isDisabled = false
    private var override: SelectionOverride? = null
    private val playingString = "<font color=#673AB7> &nbsp;(playing) &nbsp; </font>"

    /**
     * Sets whether adaptive selections (consisting of more than one track) can be made using this
     * selection view.
     *
     *
     * For the view to enable adaptive selection it is necessary both for this feature to be
     * enabled, and for the target renderer to support adaptation between the available tracks.
     *
     * @param allowAdaptiveSelections Whether adaptive selection is enabled.
     */
    fun setAllowAdaptiveSelections(allowAdaptiveSelections: Boolean) {
        if (this.allowAdaptiveSelections != allowAdaptiveSelections) {
            this.allowAdaptiveSelections = allowAdaptiveSelections
            updateViews()
        }
    }

    /**
     * Sets whether an option is available for disabling the renderer.
     *
     * @param showDisableOption Whether the disable option is shown.
     */
    fun setShowDisableOption(showDisableOption: Boolean) {
        disableView.visibility = if (showDisableOption) VISIBLE else GONE
    }

    /**
     * Sets the [TrackNameProvider] used to generate the user visible name of each track and
     * updates the view with track names queried from the specified provider.
     *
     * @param trackNameProvider The [TrackNameProvider] to use.
     */
    fun setTrackNameProvider(trackNameProvider: TrackNameProvider?) {
        this.trackNameProvider = Assertions.checkNotNull(trackNameProvider)
        updateViews()
    }

    /**
     * Initialize the view to select tracks for a specified renderer using a [ ].
     *
     * @param trackSelector The [DefaultTrackSelector].
     * @param rendererIndex The index of the renderer.
     */
    fun init(trackSelector: DefaultTrackSelector?, rendererIndex: Int) {
        this.trackSelector = trackSelector
        this.rendererIndex = rendererIndex
        updateViews()
    }

    // Private methods.
    private fun updateViews() {
        // Remove previous per-track views.
        for (i in childCount - 1 downTo 3) {
            removeViewAt(i)
        }
        val trackInfo = if (trackSelector == null) null else trackSelector!!.currentMappedTrackInfo
        if (trackSelector == null || trackInfo == null) {
            // The view is not initialized.
            disableView.isEnabled = false
            defaultView.isEnabled = false
            return
        }
        disableView.isEnabled = true
        defaultView.isEnabled = true
        trackGroups = trackInfo.getTrackGroups(rendererIndex)
        val parameters = trackSelector!!.parameters
        isDisabled = parameters.getRendererDisabled(rendererIndex)
        override = parameters.getSelectionOverride(rendererIndex, trackGroups!!)

        // Add per-track views.
        trackViews = arrayOfNulls(trackGroups!!.length)
        for (groupIndex in 0 until trackGroups!!.length) {
            val group = trackGroups!![groupIndex]
            val enableAdaptiveSelections = (allowAdaptiveSelections
                    && trackGroups!![groupIndex].length > 1 && (trackInfo.getAdaptiveSupport(rendererIndex, groupIndex, false)
                    != RendererCapabilities.ADAPTIVE_NOT_SUPPORTED))
            trackViews[groupIndex] = arrayOfNulls(group.length)
            for (trackIndex in 0 until group.length) {
                if (trackIndex == 0) {
                    addView(inflater.inflate(com.google.android.exoplayer2.ui.R.layout.exo_list_divider, this, false))
                }
                val trackViewLayoutId = if (enableAdaptiveSelections) android.R.layout.simple_list_item_single_choice else android.R.layout.simple_list_item_single_choice
                val trackView = inflater.inflate(trackViewLayoutId, this, false) as CheckedTextView
                trackView.setBackgroundResource(selectableItemBackgroundResourceId)
                trackView.text = Html.fromHtml(buildBitrateString(group.getFormat(trackIndex)))
                if (trackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex)
                        == RendererCapabilities.FORMAT_HANDLED) {
                    trackView.isFocusable = true
                    trackView.tag = Pair.create(groupIndex, trackIndex)
                    trackView.setOnClickListener(componentListener)
                } else {
                    trackView.isFocusable = false
                    trackView.isEnabled = false
                }
                trackViews[groupIndex][trackIndex] = trackView
                addView(trackView)
            }
        }
        updateViewStates()
    }

    private fun updateViewStates() {
        disableView.isChecked = isDisabled
        defaultView.isChecked = !isDisabled && override == null
        for (i in trackViews.indices) {
            for (j in trackViews[i].indices) {
                trackViews[i][j]!!.isChecked = override != null && override!!.groupIndex == i && override!!.containsTrack(j)
                //                Log.d(TAG, "override.groupIndex" + override.groupIndex + " override.containsTrack(j) " + override.containsTrack(j));
            }
        }
    }

    private fun applySelection() {
        val parametersBuilder = trackSelector!!.buildUponParameters()
        parametersBuilder.setRendererDisabled(rendererIndex, isDisabled)
        if (override != null) {
            parametersBuilder.setSelectionOverride(rendererIndex, trackGroups!!, override)
        } else {
            parametersBuilder.clearSelectionOverrides(rendererIndex)
        }
        trackSelector!!.setParameters(parametersBuilder)
    }

    private fun onClick(view: View) {
        if (view === disableView) {
            onDisableViewClicked()
        } else if (view === defaultView) {
            onDefaultViewClicked()
        } else {
            onTrackViewClicked(view)
        }
        updateViewStates()
    }

    private fun onDisableViewClicked() {
        isDisabled = true
        override = null
    }

    private fun onDefaultViewClicked() {
        isDisabled = false
        override = null
    }

    private fun onTrackViewClicked(view: View) {
        isDisabled = false
        val tag = view.tag as Pair<Int, Int>
        val groupIndex = tag.first
        val trackIndex = tag.second
        if (override == null) {
            override = SelectionOverride(groupIndex, trackIndex)
        } else {
            val overrideTracks = override!!.tracks
            val tracks = getTracksRemoving(overrideTracks, override!!.tracks[0])
            override = SelectionOverride(groupIndex, *tracks)
            override = SelectionOverride(groupIndex, trackIndex)
        }
    }

    // Internal classes.
    private inner class ComponentListener : OnClickListener {
        override fun onClick(view: View) {
            this@MyTrackSelectionView.onClick(view)
        }
    }

    private fun buildBitrateString(format: Format): String {
        val bitrate = format.bitrate
        val isPlaying = currentBitrate == bitrate.toLong()
        if (bitrate == Format.NO_VALUE) {
            return updateText(isPlaying, trackNameProvider.getTrackName(format))
        }
        if (bitrate <= BITRATE_160P) {
            return updateText(isPlaying, " 160P")
        }
        if (bitrate <= BITRATE_240P) {
            return updateText(isPlaying, " 240P")
        }
        if (bitrate <= BITRATE_360P) {
            return updateText(isPlaying, " 360P")
        }
        if (bitrate <= BITRATE_480P) {
            return updateText(isPlaying, " 480P")
        }
        if (bitrate <= BITRATE_720P) {
            return updateText(isPlaying, " 720P")
        }
        return if (bitrate <= BITRATE_1080P) {
            updateText(isPlaying, " 1080P")
        } else trackNameProvider.getTrackName(format)
    }

    private fun updateText(isPlaying: Boolean, quality: String): String {
        return if (isPlaying) {
            if (!quality.contains(playingString)) quality + playingString else quality
        } else quality.replace(playingString, "")
    }

    companion object {
        private const val TAG = "MyTrackSelectionView"
        private var currentBitrate: Long = 0
        private const val BITRATE_1080P = 2800000
        private const val BITRATE_720P = 1600000
        private const val BITRATE_480P = 700000
        private const val BITRATE_360P = 530000
        private const val BITRATE_240P = 400000
        private const val BITRATE_160P = 300000
        fun getDialog(
                activity: Activity?,
                trackSelector: DefaultTrackSelector?,
                rendererIndex: Int,
                currentBitrate: Long): Pair<AlertDialog, MyTrackSelectionView> {
            val builder = AlertDialog.Builder(activity)
            Companion.currentBitrate = currentBitrate

            // Inflate with the builder's context to ensure the correct style is used.
            val dialogInflater = LayoutInflater.from(builder.context)
            val dialogView = dialogInflater.inflate(com.google.android.exoplayer2.ui.R.layout.exo_track_selection_dialog, null)
            val selectionView: MyTrackSelectionView = dialogView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_track_selection_view)
            selectionView.init(trackSelector, rendererIndex)
            val okClickListener = DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> selectionView.applySelection() }
            val dialog = builder
                    .setView(dialogView)
                    .setPositiveButton(android.R.string.ok, okClickListener)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
            return Pair.create(dialog, selectionView)
        }

        private fun getTracksAdding(tracks: IntArray, addedTrack: Int): IntArray {
            var tracks = tracks
            tracks = Arrays.copyOf(tracks, tracks.size + 1)
            tracks[tracks.size - 1] = addedTrack
            return tracks
        }

        private fun getTracksRemoving(tracks: IntArray, removedTrack: Int): IntArray {
            val newTracks = IntArray(tracks.size - 1)
            var trackCount = 0
            for (track in tracks) {
                if (track != removedTrack) {
                    newTracks[trackCount++] = track
                }
            }
            return newTracks
        }
    }

    init {
        val attributeArray = context
                .theme
                .obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        selectableItemBackgroundResourceId = attributeArray.getResourceId(0, 0)
        Log.e(TAG, "MyTrackSelectionView: $selectableItemBackgroundResourceId")
        attributeArray.recycle()
        inflater = LayoutInflater.from(context)
        componentListener = ComponentListener()
        trackNameProvider = DefaultTrackNameProvider(resources)

        // View for disabling the renderer.
        disableView = inflater.inflate(android.R.layout.simple_list_item_single_choice, this, false) as CheckedTextView
        disableView.setBackgroundResource(selectableItemBackgroundResourceId)
        disableView.setText(R.string.exo_track_selection_none)
        disableView.isEnabled = false
        disableView.isFocusable = true
        disableView.setOnClickListener(componentListener)
        disableView.visibility = GONE
        addView(disableView)
        // Divider view.
        addView(inflater.inflate(R.layout.exo_list_divider, this, false))
        // View for clearing the override to allow the selector to use its default selection logic.
        defaultView = inflater.inflate(android.R.layout.simple_list_item_single_choice, this, false) as CheckedTextView
        defaultView.setBackgroundResource(selectableItemBackgroundResourceId)
        defaultView.setText(R.string.exo_track_selection_auto)
        defaultView.isEnabled = false
        defaultView.isFocusable = true
        defaultView.setOnClickListener(componentListener)
        addView(defaultView)
    }
}