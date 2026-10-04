package com.farsitel.bazaar.updater

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.farsitel.bazaar.updater.VersionParser.parseUpdateResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.lang.ref.WeakReference

public object BazaarUpdater {

    private var connection: WeakReference<UpdateServiceConnection>? = null
    private var updateDownloadedConnection: WeakReference<UpdateDownloadedServiceConnection>? = null

    @JvmStatic
    public fun getLastUpdateState(
        context: Context,
        listener: OnUpdateResult,
    ) {
        getLastUpdateState(
            context = context,
            scope = retrieveScope(context),
            listener = listener,
        )
    }

    @JvmSynthetic
    public fun getLastUpdateState(
        context: Context,
        scope: CoroutineScope,
        listener: OnUpdateResult,
    ) {
        if (verifyBazaarIsInstalled(context).not()) {
            listener.onResult(UpdateResult.Error(BazaarIsNotInstalledException()))
        } else {
            initService(
                context = context,
                scope = scope,
                listener = listener,
            )
        }
    }

    @JvmStatic
    public fun updateApplication(context: Context) {
        val intent = if (verifyBazaarIsInstalled(context).not()) {
            Intent(Intent.ACTION_VIEW, "$BAZAAR_WEB_APP_DETAIL${context.packageName}".toUri())
        } else {
            Intent(
                Intent.ACTION_VIEW,
                "$BAZAAR_THIRD_PARTY_APP_DETAIL${context.packageName}".toUri(),
            ).apply {
                setPackage(BAZAAR_PACKAGE_NAME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }

    /**
     * Tells whether Bazaar holds an update for this application that was already
     * downloaded but never installed, for example because the device refused the
     * silent install that the Bazaar scheduler attempted.
     *
     * When it answers true, [installDownloadedUpdate] is the way to finish that
     * update.
     */
    @JvmStatic
    public fun isUpdateDownloaded(
        context: Context,
        listener: OnUpdateDownloadedResult,
    ) {
        isUpdateDownloaded(
            context = context,
            scope = retrieveScope(context),
            listener = listener,
        )
    }

    @JvmSynthetic
    public fun isUpdateDownloaded(
        context: Context,
        scope: CoroutineScope,
        listener: OnUpdateDownloadedResult,
    ) {
        if (verifyBazaarIsInstalled(context).not()) {
            listener.onResult(UpdateDownloadedResult.Error(BazaarIsNotInstalledException()))
        } else if (isUpdateDownloadedSupported(context).not()) {
            listener.onResult(UpdateDownloadedResult.Error(updateDownloadedNotSupported()))
        } else {
            initUpdateDownloadedService(
                context = context,
                scope = scope,
                listener = listener,
            )
        }
    }

    /**
     * Opens Bazaar's install flow for the update it has already downloaded for this
     * application, so the user only sees the system install dialog on top of your
     * screen. Nothing is downloaded again: Bazaar installs the file it is holding.
     *
     * Call it from a foreground screen when [isUpdateDownloaded] answers true. On a
     * device where Bazaar is not allowed to install apps yet, Bazaar asks for that
     * permission in this same flow and finishes the install once it is granted.
     */
    @JvmStatic
    public fun installDownloadedUpdate(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$BAZAAR_THIRD_PARTY_INSTALL_DOWNLOADED_UPDATE${context.packageName}".toUri(),
        ).apply {
            setPackage(BAZAAR_PACKAGE_NAME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (ignored: ActivityNotFoundException) {
            // Installed Bazaar version does not support this deep link yet.
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun retrieveScope(
        context: Context,
    ): CoroutineScope {
        return if (context is LifecycleOwner) {
            context.lifecycleScope
        } else {
            GlobalScope
        }
    }

    private fun initUpdateDownloadedService(
        context: Context,
        scope: CoroutineScope,
        listener: OnUpdateDownloadedResult,
    ) {
        lateinit var con: UpdateDownloadedServiceConnection
        con = UpdateDownloadedServiceConnection(
            packageName = context.packageName,
            scope = scope,
            onResult = { isDownloaded ->
                listener.onResult(UpdateDownloadedResult.Result(isDownloaded))
                releaseUpdateDownloadedService(context, con)
            },
            onError = { throwable ->
                listener.onResult(UpdateDownloadedResult.Error(throwable))
                releaseUpdateDownloadedService(context, con)
            },
        )
        updateDownloadedConnection = WeakReference(con)
        if (bindUpdateCheckService(context, con).not()) {
            listener.onResult(UpdateDownloadedResult.Error(UnknownException()))
            releaseUpdateDownloadedService(context, con)
        }
    }

    private fun bindUpdateCheckService(
        context: Context,
        con: UpdateDownloadedServiceConnection,
    ): Boolean {
        val intent = Intent(BAZAAR_UPDATE_INTENT)
        intent.setPackage(BAZAAR_PACKAGE_NAME)
        return try {
            val isBound = context.bindService(intent, con, Context.BIND_AUTO_CREATE)
            // From here on the connection is registered with the system and
            // must be released exactly once via unbindService.
            synchronized(this) { con.isBound = isBound }
            isBound
        } catch (ignored: Exception) {
            false
        }
    }

    /**
     * Un-binds the given connection. Safe to call more than once for the same or
     * overlapping connections: each connection is unbound exactly once, and a
     * stale/never-registered connection is a no-op.
     */
    private fun releaseUpdateDownloadedService(
        context: Context,
        con: UpdateDownloadedServiceConnection,
    ) {
        val shouldUnbind = synchronized(this) {
            val wasBound = con.isBound
            con.isBound = false
            if (updateDownloadedConnection?.get() === con) {
                updateDownloadedConnection = null
            }
            wasBound
        }
        if (shouldUnbind) {
            try {
                context.unbindService(con)
            } catch (ignored: IllegalArgumentException) {
                // Already unbound, or the bind never fully registered.
            }
        }
    }

    private fun isUpdateDownloadedSupported(context: Context): Boolean {
        return getBazaarVersionCode(context) >= BAZAAR_CODE_UPDATE_DOWNLOADED_SUPPORTED
    }

    private fun updateDownloadedNotSupported(): Throwable {
        return BazaarIsNotUpdate(
            "The downloaded-update check is supported in bazaar version" +
                " $BAZAAR_CODE_UPDATE_DOWNLOADED_SUPPORTED and above",
        )
    }

    private fun initService(
        context: Context,
        scope: CoroutineScope,
        listener: OnUpdateResult,
    ) {
        connection = WeakReference(
            UpdateServiceConnection(
                packageName = context.packageName,
                scope = scope,
                bazaarVersionCode = getBazaarVersionCode(context),
                onResult = { targetVersion ->
                    listener.onResult(
                        parseUpdateResponse(
                            version = targetVersion,
                            context = context,
                        ),
                    )
                    releaseService(context)
                },
                onError = { message ->
                    listener.onResult(UpdateResult.Error(message))
                    releaseService(context)
                },
            ),
        )

        val intent = Intent(BAZAAR_UPDATE_INTENT)
        intent.setPackage(BAZAAR_PACKAGE_NAME)
        try {
            connection?.get()?.let { con ->
                context.bindService(intent, con, Context.BIND_AUTO_CREATE)
            }
        } catch (e: Exception) {
            releaseService(context)
        }
    }

    /** This is our function to un-binds this activity from our service.  */
    private fun releaseService(context: Context) {
        connection?.get()?.let { con -> context.unbindService(con) }
        connection = null
    }
}
