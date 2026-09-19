package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.preference.compose.PreferenceRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Backup screen — two idle-state sections (export / restore). Dialogs
// (ExportPassword, ImportPassword, ImportConfirm) fire off VM state and
// are covered indirectly by BackupViewModel unit tests; only the resting
// screen shape needs a golden.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class BackupScreenScreenshotTest {
    @Test fun backupScreen() = captureMatrix("backup_screen") { BackupScreenPreview() }
}

@Composable
private fun BackupScreenPreview() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(id = R.dimen.margin2x),
                vertical = dimensionResource(id = R.dimen.margin1x),
            ),
    ) {
        item {
            PreferenceSection(text = stringResource(id = R.string.backup_section_local)) {
                PreferenceRow(
                    title = stringResource(id = R.string.backup_export_title),
                    summary = stringResource(id = R.string.backup_export_summary),
                )
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.backup_section_restore)) {
                PreferenceRow(
                    title = stringResource(id = R.string.backup_import_title),
                    summary = stringResource(id = R.string.backup_import_summary),
                )
            }
        }
    }
}
