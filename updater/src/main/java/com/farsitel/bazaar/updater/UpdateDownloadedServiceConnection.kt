package com.farsitel.bazaar.updater

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.farsitel.bazaar.IUpdateCheckService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class UpdateDownloadedServiceConnection(
    private val packageName: String,
    private val scope: CoroutineScope,
    private val onResult: (Boolean) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ServiceConnection {

    /**
     * Tracks whether this specific connection is currently registered with the
     * system. Guarded by the lock in [BazaarUpdater] so release happens exactly
     * once per instance.
     */
    @JvmField
    internal var isBound: Boolean = false

    override fun onServiceConnected(name: ComponentName?, boundService: IBinder?) {
        try {
            val service = IUpdateCheckService.Stub.asInterface(boundService)
            scope.launch(Dispatchers.IO) {
                try {
                    val isDownloaded = service?.isUpdateDownloaded(packageName)
                    if (isDownloaded != null) {
                        onResult(isDownloaded)
                    } else {
                        onError(UnknownException())
                    }
                } catch (throwable: Throwable) {
                    // e.g. DeadObjectException/RemoteException when the Bazaar
                    // service process dies before the transaction completes.
                    onError(throwable)
                }
            }
        } catch (throwable: Throwable) {
            onError(throwable)
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName?) {
        onError(ServiceDisconnectionException(componentName))
    }
}
