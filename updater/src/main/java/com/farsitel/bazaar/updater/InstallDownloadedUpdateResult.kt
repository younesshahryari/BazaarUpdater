@file:JvmName("-InstallDownloadedUpdateResult")

package com.farsitel.bazaar.updater

public sealed class InstallDownloadedUpdateResult {

    public object Success : InstallDownloadedUpdateResult()

    public data class Error(val throwable: Throwable) : InstallDownloadedUpdateResult()

    public fun isSuccess(): Boolean = this is Success

    public fun getError(): Throwable? = (this as? Error)?.throwable
}

public inline fun InstallDownloadedUpdateResult.doOnSuccess(
    call: () -> Unit,
): InstallDownloadedUpdateResult {
    if (this is InstallDownloadedUpdateResult.Success) call()
    return this
}

public inline fun InstallDownloadedUpdateResult.doOnError(
    call: (Throwable) -> Unit,
): InstallDownloadedUpdateResult {
    if (this is InstallDownloadedUpdateResult.Error) call(throwable)
    return this
}
