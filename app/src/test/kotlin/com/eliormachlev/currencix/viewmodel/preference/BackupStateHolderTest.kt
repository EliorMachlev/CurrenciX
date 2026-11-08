package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.viewmodel.util.RetainedScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val FILE: Uri = Uri.parse("content://backup/currencix.bak")

// The backup dialogs and the passwords they carry: a password is zeroed when
// nobody needs it any more, and handed on intact when the import will.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupStateHolderTest {
    private val retained = RetainedScope()
    private val holder = BackupStateHolder(ApplicationProvider.getApplicationContext(), retained)

    @Test
    fun `nothing is open or busy to begin with`() {
        assertNull(holder.dialog)
        assertFalse(holder.busy)
    }

    @Test
    fun `the export prompt opens and dismisses`() {
        holder.openExportPasswordPrompt()
        assertEquals(BackupDialog.ExportPassword, holder.dialog)

        holder.dismissDialog()
        assertNull(holder.dialog)
    }

    @Test
    fun `the export password is kept for the file picker, handed over once`() {
        val password = "correct horse".toCharArray()
        holder.openExportPasswordPrompt()

        holder.stashExportPasswordAndDismiss(password)

        assertNull(holder.dialog)
        assertArrayEquals("correct horse".toCharArray(), holder.consumePendingExportPassword())
        assertNull(holder.consumePendingExportPassword())
    }

    @Test
    fun `an export without a password stashes none`() {
        holder.stashExportPasswordAndDismiss(null)

        assertNull(holder.consumePendingExportPassword())
    }

    @Test
    fun `stashing a new export password zeroes the one it replaces`() {
        val first = "first-password".toCharArray()
        holder.stashExportPasswordAndDismiss(first)

        holder.stashExportPasswordAndDismiss("second-password".toCharArray())

        assertTrue(first.all { it == '\u0000' })
    }

    @Test
    fun `confirming an import password opens the confirmation with it`() {
        holder.confirmImportPassword(FILE, "secret".toCharArray())

        assertEquals(BackupDialog.ImportConfirm(FILE, "secret".toCharArray()), holder.dialog)
    }

    @Test
    fun `dismissing the import confirmation zeroes its password`() {
        val password = "secret".toCharArray()
        holder.confirmImportPassword(FILE, password)

        holder.dismissDialog()

        assertNull(holder.dialog)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `closing the import confirmation for the import leaves its password intact`() {
        val password = "secret".toCharArray()
        holder.confirmImportPassword(FILE, password)

        holder.closeImportConfirmation()

        assertNull(holder.dialog)
        assertArrayEquals("secret".toCharArray(), password)
    }

    @Test
    fun `a wrong password reopens the prompt as a retry`() {
        holder.promptPasswordRetry(FILE)

        assertEquals(BackupDialog.ImportPassword(FILE, isRetry = true), holder.dialog)
    }

    @Test
    fun `letting the holder go zeroes the passwords it holds`() {
        val exportPassword = "export-pass".toCharArray()
        val importPassword = "import-pass".toCharArray()
        holder.stashExportPasswordAndDismiss(exportPassword)
        holder.confirmImportPassword(FILE, importPassword)

        retained.onRetired()

        assertTrue(exportPassword.all { it == '\u0000' })
        assertTrue(importPassword.all { it == '\u0000' })
        assertNull(holder.dialog)
        assertNull(holder.consumePendingExportPassword())
    }

    @Test
    fun `import confirmations compare their password by content`() {
        val a = BackupDialog.ImportConfirm(FILE, "x".toCharArray())

        assertEquals(a, BackupDialog.ImportConfirm(FILE, "x".toCharArray()))
        assertFalse(a == BackupDialog.ImportConfirm(FILE, "y".toCharArray()))
        assertFalse(a == BackupDialog.ImportConfirm(FILE, null))
        assertEquals(BackupDialog.ImportConfirm(FILE, null), BackupDialog.ImportConfirm(FILE, null))
    }
}
