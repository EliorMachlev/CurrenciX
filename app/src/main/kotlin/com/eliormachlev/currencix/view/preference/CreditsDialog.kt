package com.eliormachlev.currencix.view.preference

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.ui.platform.ComposeView
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.preference.compose.CREDITS_SECTIONS
import com.eliormachlev.currencix.view.preference.compose.CreditsList

class CreditsDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view =
            ComposeView(context).apply {
                setContent {
                    AppTheme {
                        CreditsList(CREDITS_SECTIONS)
                    }
                }
            }
        return AlertDialog
            .Builder(context)
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.title_credits)
            .setView(view)
            .create()
    }
}
