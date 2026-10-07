package com.farsitel.bazaar.updater

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.farsitel.bazaar.IPrepareInstallCallback
import com.farsitel.bazaar.IUpdateCheckService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
public class BazaarUpdaterTest {

    private lateinit var application: Application

    @Before
    public fun setUp() {
        application = RuntimeEnvironment.getApplication()
    }

    @Test
    public fun `listener overload sends prepared pending intent and reports success once on main`() {
        val pendingIntent = bazaarPendingIntent(1, "prepared")
        val service = FakeUpdateService { _, callback ->
            callback.onSuccess(pendingIntent)
            callback.onError()
        }
        val context = ServiceContext(application, service, BAZAAR_CODE_PREPARE_INSTALL_SUPPORTED)
        val results = mutableListOf<InstallDownloadedUpdateResult>()
        var callbackOnMain = false

        BazaarUpdater.installDownloadedUpdate(context) { result ->
            callbackOnMain = Looper.myLooper() == Looper.getMainLooper()
            results += result
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("prepared", shadowOf(pendingIntent).savedIntent.action)
        assertTrue(shadowOf(pendingIntent).isActivity)
        assertTrue(results.single() is InstallDownloadedUpdateResult.Success)
        assertEquals(1, context.unbindCount)
        assertTrue(callbackOnMain)
    }

    @Test
    public fun `listener overload rejects pending intent created by another package`() {
        val pendingIntent = PendingIntent.getActivity(
            application,
            2,
            Intent("foreign").setPackage(application.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val context = ServiceContext(
            application,
            FakeUpdateService { _, callback -> callback.onSuccess(pendingIntent) },
            BAZAAR_CODE_PREPARE_INSTALL_SUPPORTED,
        )
        val results = mutableListOf<InstallDownloadedUpdateResult>()

        BazaarUpdater.installDownloadedUpdate(context, results::add)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(results.single() is InstallDownloadedUpdateResult.Error)
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(1, context.unbindCount)
    }

    @Test
    public fun `listener overload reports generic error when bind fails`() {
        val context = ServiceContext(
            application,
            FakeUpdateService { _, _ -> error("must not be called") },
            BAZAAR_CODE_PREPARE_INSTALL_SUPPORTED,
            bindResult = false,
        )
        val results = mutableListOf<InstallDownloadedUpdateResult>()

        BazaarUpdater.installDownloadedUpdate(context, results::add)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(results.single() is InstallDownloadedUpdateResult.Error)
        assertEquals(0, context.unbindCount)
    }

    @Test
    public fun `void overload uses prepare flow when supported`() {
        val pendingIntent = bazaarPendingIntent(3, "prepared-void")
        val context = ServiceContext(
            application,
            FakeUpdateService { _, callback -> callback.onSuccess(pendingIntent) },
            BAZAAR_CODE_PREPARE_INSTALL_SUPPORTED,
        )

        BazaarUpdater.installDownloadedUpdate(context)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("prepared-void", shadowOf(pendingIntent).savedIntent.action)
        assertEquals(1, context.unbindCount)
    }

    @Test
    public fun `void overload uses legacy deep link below supported version`() {
        val context = ServiceContext(
            application,
            FakeUpdateService { _, _ -> error("must not be called") },
            BAZAAR_CODE_PREPARE_INSTALL_SUPPORTED - 1,
        )

        BazaarUpdater.installDownloadedUpdate(context)

        val intent = context.startedActivities.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(BAZAAR_PACKAGE_NAME, intent.`package`)
        assertEquals(
            "$BAZAAR_THIRD_PARTY_INSTALL_DOWNLOADED_UPDATE${application.packageName}",
            intent.data.toString(),
        )
        assertEquals(0, context.bindCount)
    }

    private fun bazaarPendingIntent(requestCode: Int, action: String): PendingIntent {
        val bazaarContext = object : ContextWrapper(application) {
            override fun getPackageName(): String = BAZAAR_PACKAGE_NAME
            override fun getOpPackageName(): String = BAZAAR_PACKAGE_NAME
        }
        return PendingIntent.getActivity(
            bazaarContext,
            requestCode,
            Intent(action).setPackage(application.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        ).also { pendingIntent ->
            ReflectionHelpers.setField(shadowOf(pendingIntent), "creatorPackage", BAZAAR_PACKAGE_NAME)
        }
    }

    private class FakeUpdateService(
        private val prepare: (String, IPrepareInstallCallback) -> Unit,
    ) : IUpdateCheckService.Stub() {
        override fun getVersionCode(packageName: String): Long = 0
        override fun getRemoteVersionCode(packageName: String): Long = 0
        override fun isUpdateDownloaded(packageName: String): Boolean = true
        override fun prepareDownloadedUpdateInstall(
            packageName: String,
            callback: IPrepareInstallCallback,
        ) {
            prepare(packageName, callback)
        }
    }

    private class ServiceContext(
        base: Context,
        private val service: IUpdateCheckService,
        bazaarVersion: Int,
        private val bindResult: Boolean = true,
    ) : ContextWrapper(base) {
        var bindCount: Int = 0
        var unbindCount: Int = 0
        var listenerCalledOnMain: Boolean = false
        val startedActivities = mutableListOf<Intent>()

        init {
            val packageInfo = PackageInfo().apply {
                packageName = BAZAAR_PACKAGE_NAME
                versionCode = bazaarVersion
                applicationInfo = ApplicationInfo().apply { packageName = BAZAAR_PACKAGE_NAME }
            }
            shadowOf(packageManager).installPackage(packageInfo)
        }

        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            bindCount++
            if (bindResult) {
                Handler(Looper.getMainLooper()).post {
                    connection.onServiceConnected(
                        ComponentName(BAZAAR_PACKAGE_NAME, "UpdateCheckService"),
                        service.asBinder(),
                    )
                }
            }
            return bindResult
        }

        override fun unbindService(connection: ServiceConnection) {
            unbindCount++
        }

        override fun startActivity(intent: Intent) {
            listenerCalledOnMain = Looper.myLooper() == Looper.getMainLooper()
            startedActivities += intent
            baseContext.startActivity(intent)
        }
    }
}
