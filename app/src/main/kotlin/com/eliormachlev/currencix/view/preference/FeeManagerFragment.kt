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
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.preference.compose.FeesScreen
import com.eliormachlev.currencix.viewmodel.preference.FeeManagerViewModel

/**
 * Compose-hosted fee manager. Was a `PreferenceFragmentCompat` with hand-rolled
 * dialog scaffolding; now a plain [Fragment] that mounts a ComposeView
 * rendering [FeesScreen]. Currency picking is fully compose-native
 * ([com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet]) — no
 * fragment/childFragmentManager plumbing required.
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
                    FeesScreen(viewModel = viewModel)
                }
            }
        }
    }
}
