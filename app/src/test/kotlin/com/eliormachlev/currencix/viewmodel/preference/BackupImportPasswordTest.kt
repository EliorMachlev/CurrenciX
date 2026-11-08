package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one starts WorkManager and rate refreshes.
@Config(sdk = [34], application = Application::class)
class BackupImportPasswordTest {
    private val uri: Uri = Uri.parse("content://backup/currencix.json")
    private val holder = BackupStateHolder(ApplicationProvider.getApplicationContext())

    private fun confirmedWith(password: CharArray) = holder.confirmImportPassword(uri, password)

    @Test
    fun `confirming a restore keeps the password for the import`() {
        val password = "correct horse".toCharArray()
        confirmedWith(password)

        holder.closeImportConfirmation()

        assertNull(holder.dialog)
        assertArrayEquals("correct horse".toCharArray(), password)
    }

    @Test
    fun `cancelling the confirmation wipes the password`() {
        val password = "correct horse".toCharArray()
        confirmedWith(password)

        holder.dismissDialog()

        assertNull(holder.dialog)
        assertArrayEquals(CharArray(password.size), password)
    }
}
