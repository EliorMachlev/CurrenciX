package de.salomax.currencies.view.cart.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import de.salomax.currencies.R

// First Compose surface migrated off XML. Rendered inside a ComposeView that
// replaces the old TextView with id `cart_empty_hint`. Kept intentionally
// small — the goal of the pilot is to prove the wiring (dependency, theme,
// live recomposition on visibility toggle) works end-to-end so the larger
// totals / row list can migrate in follow-up phases.
//
// MaterialTheme is wired here (rather than at the activity level) because
// this is currently the only Compose surface in CartActivity; once more
// composables move over, hoist the theme wrapper up to a single call site.
@Composable
fun CartEmptyHint() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(id = R.dimen.margin3x)),
        ) {
            Text(
                text = stringResource(id = R.string.cart_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
