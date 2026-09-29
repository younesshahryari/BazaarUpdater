@file:JvmName("-PendingInstallResult")

package com.farsitel.bazaar.updater

/**
 * What Bazaar can say about a download it holds for the calling application.
 */
public enum class PendingInstallStatus {
    /** A downloaded update exists and Bazaar is able to install it right now. */
    AVAILABLE,

    /** Bazaar has no download waiting for this package. */
    NO_PENDING_UPDATE,

    /** The installed version is already at or above the downloaded one. */
    ALREADY_INSTALLED,

    /** Bazaar remembers a download for this package but the apk file is gone. */
    NOT_DOWNLOADED,

    /** Bazaar cannot open the system installer because it is missing the install permission. */
    INSTALL_PERMISSION_REQUIRED,

    /** The caller does not own the package it asked about. */
    ACCESS_DENIED,

    /** The download is an app bundle, which this API cannot install yet. */
    UNSUPPORTED_PACKAGE_TYPE,

    /** The install could not be prepared even though the download is in place. */
    FAILED,

    /** Bazaar answered with a status this SDK does not know. */
    UNKNOWN,
}

public sealed class PendingInstallResult {

    public data class State(
        @JvmSynthetic
        internal val statusValue: Int,

        @JvmSynthetic
        internal val targetVersion: Long,

        @JvmSynthetic
        internal val installedVersion: Long,
    ) : PendingInstallResult()

    public data class Error(val throwable: Throwable) : PendingInstallResult()

    /**
     * The status behind this answer, or [PendingInstallStatus.UNKNOWN] when Bazaar
     * could not be reached at all.
     */
    public fun getStatus(): PendingInstallStatus {
        return (this as? State)?.let { pendingInstallStatusOf(it.statusValue) }
            ?: PendingInstallStatus.UNKNOWN
    }

    /** True when Bazaar holds a downloaded update it can install for this app. */
    public fun isPendingInstallAvailable(): Boolean {
        return getStatus() == PendingInstallStatus.AVAILABLE
    }

    /** The downloaded version, or [BAZAAR_ERROR_RESULT] when there is none. */
    public fun getTargetVersionCode(): Long {
        return (this as? State)?.targetVersion ?: BAZAAR_ERROR_RESULT
    }

    /** The version currently installed on the device. */
    public fun getInstalledVersionCode(): Long {
        return (this as? State)?.installedVersion ?: BAZAAR_ERROR_RESULT
    }

    public fun getError(): Throwable? {
        return (this as? Error)?.throwable
    }
}

public inline fun PendingInstallResult.doOnPendingInstallAvailable(call: () -> Unit): PendingInstallResult {
    if (isPendingInstallAvailable()) call()
    return this
}

public inline fun PendingInstallResult.doOnError(call: (Throwable) -> Unit): PendingInstallResult {
    if (this is PendingInstallResult.Error) call(throwable)
    return this
}

internal fun pendingInstallStatusOf(value: Int): PendingInstallStatus {
    return when (value) {
        0 -> PendingInstallStatus.AVAILABLE
        1 -> PendingInstallStatus.NO_PENDING_UPDATE
        2 -> PendingInstallStatus.ALREADY_INSTALLED
        3 -> PendingInstallStatus.NOT_DOWNLOADED
        4 -> PendingInstallStatus.INSTALL_PERMISSION_REQUIRED
        5 -> PendingInstallStatus.ACCESS_DENIED
        6 -> PendingInstallStatus.UNSUPPORTED_PACKAGE_TYPE
        7 -> PendingInstallStatus.FAILED
        else -> PendingInstallStatus.UNKNOWN
    }
}
