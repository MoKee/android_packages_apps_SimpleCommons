package com.simplemobiletools.commons.dialogs

import android.app.Activity
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.R
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.launchViewIntent
import com.simplemobiletools.commons.extensions.setupDialogStuff
import kotlinx.android.synthetic.main.dialog_textview.view.*

class AppSideloadedDialog(val activity: Activity, val callback: () -> Unit) {
    var dialog: AlertDialog
    val url = "https://play.google.com/store/apps/details?id=${activity.baseConfig.appId.removeSuffix(".debug")}"

    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_textview, null).apply {
            val text = String.format(activity.getString(R.string.sideloaded_app), url)
            text_view.text = Html.fromHtml(text)
            text_view.movementMethod = LinkMovementMethod.getInstance()
        }

        dialog = AlertDialog.Builder(activity)
                .setNegativeButton(R.string.cancel) { dialog, which -> negativePressed() }
                .setPositiveButton(R.string.download, null)
                .setOnDismissListener { negativePressed() }
                .create().apply {
                    activity.setupDialogStuff(view, this)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        downloadApp()
                    }
                }
    }

    private fun downloadApp() {
        activity.launchViewIntent(url)
    }

    private fun negativePressed() {
        dialog.dismiss()
        callback()
    }
}
