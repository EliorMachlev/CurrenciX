package com.eliormachlev.currencix.view.preference.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.hapticClickable

@Composable
fun CreditsList(sections: List<CreditsSection>) {
    val horizontal = dimensionResource(id = R.dimen.margin3x)
    val vertical = dimensionResource(id = R.dimen.margin2x)
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontal, vertical = vertical),
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "header-${section.headerRes}") {
                if (index > 0) Spacer(Modifier.height(vertical))
                SectionHeader(text = stringResource(id = section.headerRes))
            }
            items(items = section.entries, key = { "${section.headerRes}-${it.url}" }) { credit ->
                CreditRow(
                    credit = credit,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(credit.url)),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        // TalkBack exposes headings so users can jump between sections; without
        // this the "Project / Legal / Source / Libraries" separators are read
        // as ordinary body text and become invisible for section navigation.
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun CreditRow(
    credit: Credit,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(
                    onClickLabel = stringResource(id = R.string.a11y_action_open),
                    onClick = onClick,
                ).padding(vertical = 8.dp)
                // Title + subtitle + optional SPDX + URL should read as one
                // focus stop; without this TalkBack lands on each Text
                // separately and forces four swipes per credit.
                .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = credit.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = credit.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        credit.license?.let { spdx ->
            Text(
                text = stringResource(id = R.string.credit_license_prefix, spdx),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = credit.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
