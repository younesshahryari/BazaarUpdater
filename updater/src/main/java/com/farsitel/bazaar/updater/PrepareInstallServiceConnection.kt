package com.farsitel.bazaar.updater

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.farsitel.bazaar.IPrepareInstallCallback
import com.farsitel.bazaar.IUpdateCheckService
import java.util.concurrent.atomic.AtomicBoolean

internal class PrepareInstallServiceConnection(
    private val context: Context,
    private val packageName: String,
    private val listener: OnInstallDownloadedUpdateResult,
) : ServiceConnection {

    private val completed = AtomicBoolean(false)
    private val unbound = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var bound: Boolean = false

    fun markBound(isBound: Boolean) {
        bound = isBound
        if (isBound.not()) {
            complete(InstallDownloadedUpdateResult.Error)
        } else if (completed.get()) {
            unbindOnce()
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        try {
            val service = IUpdateCheckService.Stub.asInterface(binder)
            if (service == null) {
                complete(InstallDownloadedUpdateResult.Error)
                return
            }
            service.prepareDownloadedUpdateInstall(
                packageName,
                object : IPrepareInstallCallback.Stub() {
                    override fun onSuccess(pendingIntent: PendingIntent?) {
                        if (pendingIntent?.creatorPackage != BAZAAR_PACKAGE_NAME) {
                            complete(InstallDownloadedUpdateResult.Error)
                            return
                        }
                        try {
                            pendingIntent.send()
                            complete(InstallDownloadedUpdateResult.Success)
                        } catch (ignored: PendingIntent.CanceledException) {
                            complete(InstallDownloadedUpdateResult.Error)
                        }
                    }

                    override fun onError() {
                        complete(InstallDownloadedUpdateResult.Error)
                    }
                },
            )
        } catch (ignored: Throwable) {
            complete(InstallDownloadedUpdateResult.Error)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        complete(InstallDownloadedUpdateResult.Error)
    }

    override fun onBindingDied(name: ComponentName?) {
        complete(InstallDownloadedUpdateResult.Error)
    }

    override fun onNullBinding(name: ComponentName?) {
        complete(InstallDownloadedUpdateResult.Error)
    }

    private fun complete(result: InstallDownloadedUpdateResult) {
        if (completed.compareAndSet(false, true).not()) return
        unbindOnce()
        mainHandler.post { listener.onResult(result) }
    }

    private fun unbindOnce() {
        if (bound && unbound.compareAndSet(false, true)) {
            try {
                context.unbindService(this)
            } catch (ignored: IllegalArgumentException) {
                // The system no longer has this connection registered.
            }
        }
    }
}
