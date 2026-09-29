@file:JvmName("-PendingInstallLaunchResult")

package com.farsitel.bazaar.updater

/**
 * Outcome of asking Bazaar to install an update it had already downloaded.
 */
public sealed class PendingInstallLaunchResult {

    /**
     * Bazaar handed over the system installer UI for the downloaded apk and it was
     * launched, so the user is now looking at the install dialog.
     */
    public object Started : PendingInstallLaunchResult()

    /** Bazaar would not start an install, and [status] says why. */
    public data class NotStarted(val status: PendingInstallStatus) : PendingInstallLaunchResult()

    public data class Error(val throwable: Throwable) : PendingInstallLaunchResult()

    public fun isStarted(): Boolean {
        return this is Started
    }

    public fun getError(): Throwable? {
        return (this as? Error)?.throwable
    }
}

public inline fun PendingInstallLaunchResult.doOnStarted(call: () -> Unit): PendingInstallLaunchResult {
    if (isStarted()) call()
    return this
}

public inline fun PendingInstallLaunchResult.doOnError(call: (Throwable) -> Unit): PendingInstallLaunchResult {
    if (this is PendingInstallLaunchResult.Error) call(throwable)
    return this
}
