package com.farsitel.bazaar.updater

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.farsitel.bazaar.IUpdateCheckService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
public class BazaarUpdaterTest {

    private lateinit var application: Application

    @Before
    public fun setUp() {
        application = RuntimeEnvironment.getApplication()
    }

    @Test
    public fun `successful prepare opens the prepared install window and reports success`() {
        val context = BazaarContext(prepareResult = true)
        val results = mutableListOf<InstallDownloadedUpdateResult>()
        var callbackOnMainThread = false

        BazaarUpdater.installDownloadedUpdate(context) { result ->
            callbackOnMainThread = Looper.myLooper() == Looper.getMainLooper()
            results += result
        }
        awaitResult(results)

        assertTrue(results.single().isSuccess())
        assertTrue(callbackOnMainThread)
        assertEquals(
            "$BAZAAR_THIRD_PARTY_INSTALL_DOWNLOADED_UPDATE${context.packageName}" +
                "&$PREPARED_QUERY",
            context.startedActivities.single().data.toString(),
        )
        assertEquals(1, context.unbindCount)
    }

    @Test
    public fun `failed prepare reports error without opening any window`() {
        val context = BazaarContext(prepareResult = false)
        val results = mutableListOf<InstallDownloadedUpdateResult>()

        BazaarUpdater.installDownloadedUpdate(context, results::add)
        awaitResult(results)

        assertTrue(results.single().getError() is InstallDownloadedUpdateException)
        assertTrue(context.startedActivities.isEmpty())
        assertEquals(1, context.unbindCount)
    }

    @Test
    public fun `missing Bazaar reports error without binding`() {
        val context = BazaarContext(prepareResult = true, isBazaarInstalled = false)
        val results = mutableListOf<InstallDownloadedUpdateResult>()

        BazaarUpdater.installDownloadedUpdate(context, results::add)
        awaitResult(results)

        assertTrue(results.single().getError() is BazaarIsNotInstalledException)
        assertEquals(0, context.bindCount)
        assertTrue(context.startedActivities.isEmpty())
    }

    @Test
    public fun `unsupported Bazaar version reports error without binding`() {
        val context = BazaarContext(
            prepareResult = true,
            bazaarVersionCode = BAZAAR_CODE_UPDATE_DOWNLOADED_SUPPORTED - 1,
        )
        val results = mutableListOf<InstallDownloadedUpdateResult>()

        BazaarUpdater.installDownloadedUpdate(context, results::add)
        awaitResult(results)

        assertTrue(results.single().getError() is BazaarIsNotUpdate)
        assertEquals(0, context.bindCount)
    }

    @Test
    public fun `fire and forget install keeps opening the plain install window`() {
        val context = BazaarContext(prepareResult = true)

        BazaarUpdater.installDownloadedUpdate(context)

        assertEquals(
            "$BAZAAR_THIRD_PARTY_INSTALL_DOWNLOADED_UPDATE${context.packageName}",
            context.startedActivities.single().data.toString(),
        )
        assertEquals(0, context.bindCount)
    }

    private fun awaitResult(results: MutableList<InstallDownloadedUpdateResult>) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        while (results.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(AWAIT_STEP_MILLIS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private inner class BazaarContext(
        private val prepareResult: Boolean,
        isBazaarInstalled: Boolean = true,
        bazaarVersionCode: Int = BAZAAR_CODE_UPDATE_DOWNLOADED_SUPPORTED,
    ) : ContextWrapper(application) {

        public var bindCount: Int = 0
        public var unbindCount: Int = 0
        public val startedActivities = mutableListOf<Intent>()

        init {
            if (isBazaarInstalled) {
                shadowOf(packageManager).installPackage(
                    PackageInfo().apply {
                        packageName = BAZAAR_PACKAGE_NAME
                        versionCode = bazaarVersionCode
                        applicationInfo = ApplicationInfo().apply {
                            packageName = BAZAAR_PACKAGE_NAME
                        }
                    },
                )
            }
        }

        override fun bindService(
            service: Intent,
            conn: ServiceConnection,
            flags: Int,
        ): Boolean {
            bindCount++
            Handler(Looper.getMainLooper()).post {
                conn.onServiceConnected(
                    ComponentName(BAZAAR_PACKAGE_NAME, "UpdateCheckService"),
                    updateCheckService.asBinder(),
                )
            }
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCount++
        }

        override fun startActivity(intent: Intent) {
            startedActivities += intent
        }

        private val updateCheckService: IUpdateCheckService = object : IUpdateCheckService.Stub() {
            override fun getVersionCode(packageName: String): Long = 0

            override fun getRemoteVersionCode(packageName: String): Long = 0

            override fun isUpdateDownloaded(packageName: String): Boolean = true

            override fun prepareDownloadedUpdateInstall(packageName: String): Boolean =
                prepareResult
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MILLIS = 5_000L
        const val AWAIT_STEP_MILLIS = 10L
    }
}
