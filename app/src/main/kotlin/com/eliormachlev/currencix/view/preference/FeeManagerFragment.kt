package com.eliormachlev.currencix.view.preference

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinnerDialog
import com.eliormachlev.currencix.view.preference.compose.FeesScreen
import com.eliormachlev.currencix.viewmodel.preference.FeeManagerViewModel

/**
 * Compose-hosted fee manager. Was a `PreferenceFragmentCompat` with
 * hand-rolled dialog scaffolding; now a plain [Fragment] that mounts a
 * ComposeView rendering [FeesScreen]. Currency picking still routes through
 * the shared XML [SearchableSpinnerDialog] because it's a DialogFragment and
 * needs the fragment's [childFragmentManager] to show — everything else is
 * pure Compose.
 */
class FeeManagerFragment : Fragment() {
    private lateinit var viewModel: FeeManagerViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel = ViewModelProvider(this)[FeeManagerViewModel::class.java]
        activity?.setTitle(R.string.fee_manager_title)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    FeesScreen(
                        viewModel = viewModel,
                        onPickCurrency = ::showCurrencyPicker,
                    )
                }
            }
        }
    }

    private fun showCurrencyPicker(
        disabled: Currency?,
        onPicked: (String) -> Unit,
    ) {
        SearchableSpinnerDialog(requireContext())
            .apply {
                setDisabledCurrency(disabled)
                onRateClicked = { rate, _ -> onPicked(rate.currency.iso4217Alpha()) }
            }.show(childFragmentManager, null)
    }
}
