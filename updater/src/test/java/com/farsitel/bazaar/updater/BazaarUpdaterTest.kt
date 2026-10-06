package com.farsitel.bazaar.updater

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.ResultReceiver
import android.app.Application
import android.app.PendingIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
public class BazaarUpdaterTest {

    private lateinit var context: Application

    @Before
    public fun setUp() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        val packageInfo = PackageInfo().apply {
            packageName = BAZAAR_PACKAGE_NAME
            versionCode = BAZAAR_CODE_INSTALL_DOWNLOADED_RESULT_SUPPORTED
            applicationInfo = ApplicationInfo().apply {
                packageName = BAZAAR_PACKAGE_NAME
            }
        }
        shadowOf(context.packageManager).installPackage(packageInfo)
    }

    @Test
    public fun `callback install adds result receiver to packaged deep link`() {
        BazaarUpdater.installDownloadedUpdate(context) {}

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(BAZAAR_PACKAGE_NAME, intent.`package`)
        assertEquals(
            "$BAZAAR_THIRD_PARTY_INSTALL_DOWNLOADED_UPDATE${context.packageName}",
            intent.data.toString(),
        )
        assertTrue(intent.hasExtra(INSTALL_RESULT_RECEIVER_EXTRA))
        assertEquals(
            context.packageName,
            intent.getParcelableExtra<PendingIntent>(INSTALL_CALLER_IDENTITY_EXTRA)?.creatorPackage,
        )
    }

    @Test
    public fun `success callback is delivered once`() {
        val results = mutableListOf<InstallDownloadedUpdateResult>()
        BazaarUpdater.installDownloadedUpdate(context, results::add)
        val receiver = shadowOf(context).nextStartedActivity
            .getParcelableExtra<ResultReceiver>(INSTALL_RESULT_RECEIVER_EXTRA)!!

        receiver.send(INSTALL_RESULT_SUCCESS, null)
        receiver.send(INSTALL_RESULT_ERROR, null)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(1, results.size)
        assertTrue(results.single().isSuccess())
    }

    @Test
    public fun `error callback returns generic error`() {
        val results = mutableListOf<InstallDownloadedUpdateResult>()
        BazaarUpdater.installDownloadedUpdate(context, results::add)
        val receiver = shadowOf(context).nextStartedActivity
            .getParcelableExtra<ResultReceiver>(INSTALL_RESULT_RECEIVER_EXTRA)!!

        receiver.send(INSTALL_RESULT_ERROR, null)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(results.single().getError() is InstallDownloadedUpdateException)
    }

    @Test
    public fun `missing Bazaar returns error without opening activity`() {
        shadowOf(context.packageManager).deletePackage(BAZAAR_PACKAGE_NAME)
        val results = mutableListOf<InstallDownloadedUpdateResult>()

        BazaarUpdater.installDownloadedUpdate(context, results::add)

        assertTrue(results.single().getError() is InstallDownloadedUpdateException)
        assertEquals(null, shadowOf(context).nextStartedActivity)
    }
}
