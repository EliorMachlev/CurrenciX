package de.salomax.currencies.view.preference

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.ui.platform.ComposeView
import de.salomax.currencies.R
import de.salomax.currencies.view.compose.AppTheme
import de.salomax.currencies.view.preference.compose.ChangelogList
import de.salomax.currencies.view.preference.compose.loadChangelogSections

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
