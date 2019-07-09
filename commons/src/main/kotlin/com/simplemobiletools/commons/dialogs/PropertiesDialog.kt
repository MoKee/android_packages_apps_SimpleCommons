package com.simplemobiletools.commons.dialogs

import android.app.Activity
import android.content.res.Resources
import android.media.ExifInterface
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.R
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.sumByInt
import com.simplemobiletools.commons.helpers.sumByLong
import com.simplemobiletools.commons.models.FileDirItem
import kotlinx.android.synthetic.main.dialog_properties.view.*
import kotlinx.android.synthetic.main.property_item.view.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class PropertiesDialog() {
    private lateinit var mInflater: LayoutInflater
    private lateinit var mPropertyView: ViewGroup
    private lateinit var mResources: Resources

    /**
     * A File Properties dialog constructor with an optional parameter, usable at 1 file selected
     *
     * @param activity request activity to avoid some Theme.AppCompat issues
     * @param path the file path
     * @param countHiddenItems toggle determining if we will count hidden files themselves and their sizes (reasonable only at directory properties)
     */
    constructor(activity: Activity, path: String, countHiddenItems: Boolean = false) : this() {
        if (!File(path).exists()) {
            activity.toast(String.format(activity.getString(R.string.source_file_doesnt_exist), path))
            return
        }

        mInflater = LayoutInflater.from(activity)
        mResources = activity.resources
        val view = mInflater.inflate(R.layout.dialog_properties, null)
        mPropertyView = view.properties_holder

        val fileDirItem = FileDirItem(path, path.getFilenameFromPath(), File(path).isDirectory, 0, 0, File(path).lastModified())
        addProperty(R.string.name, fileDirItem.name)
        addProperty(R.string.path, fileDirItem.getParentPath())
        addProperty(R.string.size, "…", R.id.properties_size)

        ensureBackgroundThread {
            val fileCount = fileDirItem.getProperFileCount(countHiddenItems)
            val size = fileDirItem.getProperSize(countHiddenItems).formatSize()
            activity.runOnUiThread {
                view.findViewById<TextView>(R.id.properties_size).property_value.text = size

                if (fileDirItem.isDirectory) {
                    view.findViewById<TextView>(R.id.properties_file_count).property_value.text = fileCount.toString()
                }
            }

            if (!fileDirItem.isDirectory) {
                val projection = arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
                val uri = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.DATA} = ?"
                val selectionArgs = arrayOf(path)
                val cursor = activity.contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        val dateModified = cursor.getLongValue(MediaStore.Images.Media.DATE_MODIFIED) * 1000L
                        updateLastModified(activity, view, dateModified)
                    } else {
                        updateLastModified(activity, view, fileDirItem.modified)
                    }
                }

                val exif = ExifInterface(fileDirItem.path)
                val latLon = FloatArray(2)
                if (exif.getLatLong(latLon)) {
                    activity.runOnUiThread {
                        addProperty(R.string.gps_coordinates, "${latLon[0]}, ${latLon[1]}")
                    }
                }

                val altitude = exif.getAltitude(0.0)
                if (altitude != 0.0) {
                    activity.runOnUiThread {
                        addProperty(R.string.altitude, "${altitude}m")
                    }
                }
            }
        }

        when {
            fileDirItem.isDirectory -> {
                addProperty(R.string.direct_children_count, fileDirItem.getDirectChildrenCount(countHiddenItems).toString())
                addProperty(R.string.files_count, "…", R.id.properties_file_count)
            }
            fileDirItem.path.isImageSlow() -> {
                fileDirItem.getResolution(activity)?.let { addProperty(R.string.resolution, it.formatAsResolution()) }
            }
            fileDirItem.path.isAudioSlow() -> {
                fileDirItem.getDuration()?.let { addProperty(R.string.duration, it) }
                fileDirItem.getSongTitle()?.let { addProperty(R.string.song_title, it) }
                fileDirItem.getArtist()?.let { addProperty(R.string.artist, it) }
                fileDirItem.getAlbum()?.let { addProperty(R.string.album, it) }
            }
            fileDirItem.path.isVideoSlow() -> {
                fileDirItem.getDuration()?.let { addProperty(R.string.duration, it) }
                fileDirItem.getResolution(activity)?.let { addProperty(R.string.resolution, it.formatAsResolution()) }
                fileDirItem.getArtist()?.let { addProperty(R.string.artist, it) }
                fileDirItem.getAlbum()?.let { addProperty(R.string.album, it) }
            }
        }

        if (fileDirItem.isDirectory) {
            addProperty(R.string.last_modified, fileDirItem.modified.formatDate(activity))
        } else {
            addProperty(R.string.last_modified, "…", R.id.properties_last_modified)
            try {
                addExifProperties(path, activity)
            } catch (e: FileNotFoundException) {
                activity.toast(R.string.unknown_error_occurred)
                return
            }
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.properties)
                }
    }

    private fun updateLastModified(activity: Activity, view: View, timestamp: Long) {
        activity.runOnUiThread {
            view.findViewById<TextView>(R.id.properties_last_modified).property_value.text = timestamp.formatDate(activity)
        }
    }

    /**
     * A File Properties dialog constructor with an optional parameter, usable at multiple items selected
     *
     * @param activity request activity to avoid some Theme.AppCompat issues
     * @param path the file path
     * @param countHiddenItems toggle determining if we will count hidden files themselves and their sizes
     */
    constructor(activity: Activity, paths: List<String>, countHiddenItems: Boolean = false) : this() {
        mInflater = LayoutInflater.from(activity)
        mResources = activity.resources
        val view = mInflater.inflate(R.layout.dialog_properties, null)
        mPropertyView = view.properties_holder

        val fileDirItems = ArrayList<FileDirItem>(paths.size)
        paths.forEach {
            val fileDirItem = FileDirItem(it, it.getFilenameFromPath(), File(it).isDirectory, 0, 0, File(it).lastModified())
            fileDirItems.add(fileDirItem)
        }

        val isSameParent = isSameParent(fileDirItems)

        addProperty(R.string.items_selected, paths.size.toString())
        if (isSameParent) {
            addProperty(R.string.path, fileDirItems[0].getParentPath())
        }

        addProperty(R.string.size, "…", R.id.properties_size)
        addProperty(R.string.files_count, "…", R.id.properties_file_count)

        ensureBackgroundThread {
            val fileCount = fileDirItems.sumByInt { it.getProperFileCount(countHiddenItems) }
            val size = fileDirItems.sumByLong { it.getProperSize(countHiddenItems) }.formatSize()
            activity.runOnUiThread {
                view.findViewById<TextView>(R.id.properties_size).property_value.text = size
                view.findViewById<TextView>(R.id.properties_file_count).property_value.text = fileCount.toString()
            }
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.properties)
                }
    }

    private fun addExifProperties(path: String, activity: Activity) {
        val exif = ExifInterface(path)
        val dateTaken = exif.getExifDateTaken(activity)
        if (dateTaken.isNotEmpty()) {
            addProperty(R.string.date_taken, dateTaken)
        }

        val cameraModel = exif.getExifCameraModel()
        if (cameraModel.isNotEmpty()) {
            addProperty(R.string.camera, cameraModel)
        }

        val exifString = exif.getExifProperties()
        if (exifString.isNotEmpty()) {
            addProperty(R.string.exif, exifString)
        }
    }

    private fun isSameParent(fileDirItems: List<FileDirItem>): Boolean {
        var parent = fileDirItems[0].getParentPath()
        for (file in fileDirItems) {
            val curParent = file.getParentPath()
            if (curParent != parent) {
                return false
            }

            parent = curParent
        }
        return true
    }

    private fun addProperty(labelId: Int, value: String?, viewId: Int = 0) {
        if (value == null)
            return

        mInflater.inflate(R.layout.property_item, mPropertyView, false).apply {
            property_label.text = mResources.getString(labelId)
            property_value.text = value
            mPropertyView.properties_holder.addView(this)

            if (viewId != 0) {
                id = viewId
            }
        }
    }
}
