@file:JvmName("-UpdateDownloadedResult")

package com.farsitel.bazaar.updater

/**
 * Whether Bazaar holds a downloaded update for this application that was never
 * installed, for example because the device refused the silent install that the
 * Bazaar scheduler attempted.
 */
public sealed class UpdateDownloadedResult {

    public data class Result(
        @JvmSynthetic
        internal val downloaded: Boolean,
    ) : UpdateDownloadedResult()

    public data class Error(val throwable: Throwable) : UpdateDownloadedResult()

    /** True when a downloaded update is waiting to be installed. */
    public fun isDownloaded(): Boolean {
        return (this as? Result)?.downloaded ?: false
    }

    public fun getError(): Throwable? {
        return (this as? Error)?.throwable
    }
}

public inline fun UpdateDownloadedResult.doOnResult(
    call: (Boolean) -> Unit,
): UpdateDownloadedResult {
    if (this is UpdateDownloadedResult.Result) call(isDownloaded())
    return this
}

public inline fun UpdateDownloadedResult.doOnError(
    call: (Throwable) -> Unit,
): UpdateDownloadedResult {
    if (this is UpdateDownloadedResult.Error) call(throwable)
    return this
}
