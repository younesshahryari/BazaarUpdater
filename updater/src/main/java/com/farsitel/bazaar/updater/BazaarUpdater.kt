package com.farsitel.bazaar.updater

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.farsitel.bazaar.PendingInstallState
import com.farsitel.bazaar.updater.VersionParser.parseUpdateResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.lang.ref.WeakReference

public object BazaarUpdater {

    private var connection: WeakReference<UpdateServiceConnection>? = null
    private var pendingInstallConnection: WeakReference<PendingInstallServiceConnection>? = null
    private var pendingInstallLaunchConnection: WeakReference<PendingInstallServiceConnection>? = null

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
     * Asks Bazaar whether it holds an update for this application that was already
     * downloaded but never installed, for example because the device refused the
     * silent install that the Bazaar scheduler attempted.
     */
    @JvmStatic
    public fun getPendingInstallState(
        context: Context,
        listener: OnPendingInstallResult,
    ) {
        getPendingInstallState(
            context = context,
            scope = retrieveScope(context),
            listener = listener,
        )
    }

    @JvmSynthetic
    public fun getPendingInstallState(
        context: Context,
        scope: CoroutineScope,
        listener: OnPendingInstallResult,
    ) {
        if (verifyBazaarIsInstalled(context).not()) {
            listener.onResult(PendingInstallResult.Error(BazaarIsNotInstalledException()))
        } else {
            initPendingInstallService(
                context = context,
                scope = scope,
                listener = listener,
            )
        }
    }

    /**
     * Asks Bazaar to install the update it has already downloaded. The user only
     * sees the system install dialog, which is opened on top of this application,
     * so the install is interactive and is not affected by the silent-install
     * restrictions of some devices.
     *
     * Call it from a foreground screen of the app, otherwise the system may refuse
     * to open the installer UI.
     */
    @JvmStatic
    public fun installPendingUpdate(
        context: Context,
        listener: OnPendingInstallLaunchResult,
    ) {
        installPendingUpdate(
            context = context,
            scope = retrieveScope(context),
            listener = listener,
        )
    }

    @JvmSynthetic
    public fun installPendingUpdate(
        context: Context,
        scope: CoroutineScope,
        listener: OnPendingInstallLaunchResult,
    ) {
        if (verifyBazaarIsInstalled(context).not()) {
            listener.onResult(PendingInstallLaunchResult.Error(BazaarIsNotInstalledException()))
        } else {
            initPendingInstallLaunchService(
                context = context,
                scope = scope,
                listener = listener,
            )
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

    private fun initPendingInstallService(
        context: Context,
        scope: CoroutineScope,
        listener: OnPendingInstallResult,
    ) {
        if (isPendingInstallSupported(context).not()) {
            listener.onResult(PendingInstallResult.Error(pendingInstallNotSupported()))
        } else {
            lateinit var con: PendingInstallServiceConnection
            con = PendingInstallServiceConnection(
                scope = scope,
                call = { service -> service.getPendingInstallState(context.packageName) },
                onState = { state ->
                    listener.onResult(state.toPendingInstallResult())
                    releasePendingInstallService(context, con)
                },
                onError = { throwable ->
                    listener.onResult(PendingInstallResult.Error(throwable))
                    releasePendingInstallService(context, con)
                },
            )
            pendingInstallConnection = WeakReference(con)
            if (bindPendingInstallService(context, con).not()) {
                listener.onResult(PendingInstallResult.Error(UnknownException()))
                releasePendingInstallService(context, con)
            }
        }
    }

    private fun initPendingInstallLaunchService(
        context: Context,
        scope: CoroutineScope,
        listener: OnPendingInstallLaunchResult,
    ) {
        if (isPendingInstallSupported(context).not()) {
            listener.onResult(PendingInstallLaunchResult.Error(pendingInstallNotSupported()))
        } else {
            lateinit var con: PendingInstallServiceConnection
            con = PendingInstallServiceConnection(
                scope = scope,
                call = { service -> service.installPendingUpdate(context.packageName) },
                onState = { state ->
                    listener.onResult(launchPendingInstall(context, state))
                    releasePendingInstallService(context, con)
                },
                onError = { throwable ->
                    listener.onResult(PendingInstallLaunchResult.Error(throwable))
                    releasePendingInstallService(context, con)
                },
            )
            pendingInstallLaunchConnection = WeakReference(con)
            if (bindPendingInstallService(context, con).not()) {
                listener.onResult(PendingInstallLaunchResult.Error(UnknownException()))
                releasePendingInstallService(context, con)
            }
        }
    }

    private fun bindPendingInstallService(
        context: Context,
        con: PendingInstallServiceConnection,
    ): Boolean {
        val intent = Intent(BAZAAR_PENDING_INSTALL_INTENT)
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
    private fun releasePendingInstallService(
        context: Context,
        con: PendingInstallServiceConnection,
    ) {
        val shouldUnbind = synchronized(this) {
            val wasBound = con.isBound
            con.isBound = false
            if (pendingInstallConnection?.get() === con) {
                pendingInstallConnection = null
            }
            if (pendingInstallLaunchConnection?.get() === con) {
                pendingInstallLaunchConnection = null
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

    /**
     * Launching the sender Bazaar built, rather than a plain intent, is what keeps
     * the apk readable for the installer: the read grant comes from Bazaar, which
     * owns the provider serving the downloaded file, instead of from the caller.
     */
    private fun launchPendingInstall(
        context: Context,
        state: PendingInstallState,
    ): PendingInstallLaunchResult {
        val status = pendingInstallStatusOf(state.status)
        val intentSender = state.installIntentSender
            ?: return PendingInstallLaunchResult.NotStarted(status)

        return try {
            intentSender.sendIntent(context, 0, null, null, null)
            PendingInstallLaunchResult.Started
        } catch (exception: Exception) {
            PendingInstallLaunchResult.Error(exception)
        }
    }

    private fun isPendingInstallSupported(context: Context): Boolean {
        return getBazaarVersionCode(context) >= BAZAAR_CODE_PENDING_INSTALL_SUPPORTED
    }

    private fun pendingInstallNotSupported(): Throwable {
        return BazaarIsNotUpdate(
            "Pending install is supported in bazaar version" +
                " $BAZAAR_CODE_PENDING_INSTALL_SUPPORTED and above",
        )
    }

    private fun PendingInstallState.toPendingInstallResult(): PendingInstallResult {
        return PendingInstallResult.State(
            statusValue = status,
            targetVersion = targetVersionCode,
            installedVersion = installedVersionCode,
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