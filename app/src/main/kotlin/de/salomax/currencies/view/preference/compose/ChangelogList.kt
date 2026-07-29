package de.salomax.currencies.view.preference.compose

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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import de.salomax.currencies.R

@Composable
fun ChangelogList(sections: List<ChangelogSection>) {
    val horizontal = dimensionResource(id = R.dimen.margin3x)
    val vertical = dimensionResource(id = R.dimen.margin2x)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontal, vertical = vertical),
    ) {
        items(items = sections, key = { it.version }) { section ->
            ChangelogSectionEntry(section)
            Spacer(Modifier.height(vertical))
        }
    }
}

@Composable
private fun ChangelogSectionEntry(section: ChangelogSection) {
    Column {
        Text(
            text = section.version,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        section.bullets.forEach { bullet ->
            Text(
                text = renderBullet(bullet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun renderBullet(raw: CharSequence): AnnotatedString {
    val html = "&#8226;&nbsp; $raw"
    val spanned = HtmlCompat.fromHtml(html.toString(), HtmlCompat.FROM_HTML_MODE_COMPACT)
    return AnnotatedString(spanned.toString())
}
