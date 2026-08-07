package com.eliormachlev.currencix.view.preference

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.ui.platform.ComposeView
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.preference.compose.ChangelogList
import com.eliormachlev.currencix.view.preference.compose.loadChangelogSections

class ChangelogDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val sections = loadChangelogSections(context)
        val view =
            ComposeView(context).apply {
                setContent {
                    AppTheme {
                        ChangelogList(sections)
                    }
                }
            }
        return AlertDialog
            .Builder(context)
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.title_changelog)
            .setView(view)
            .create()
    }
}
