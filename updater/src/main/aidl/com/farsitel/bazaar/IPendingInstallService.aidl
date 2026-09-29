package com.farsitel.bazaar;

import com.farsitel.bazaar.PendingInstallState;

/**
 * Lets an application ask Bazaar to finish an update that was already downloaded
 * but never installed, for the application itself only.
 *
 * The caller is identified by the uid behind the package name it passes, so an
 * application can only ever query and install its own package.
 *
 * Requires Bazaar 29.3.0 or higher.
 */
interface IPendingInstallService {

    /**
     * Reports the download that Bazaar holds for [packageName] and whether it can
     * be installed right now. The installIntentSender of the answer is always null
     * on this call.
     */
    PendingInstallState getPendingInstallState(String packageName);

    /**
     * Same answer as [getPendingInstallState], plus an installIntentSender the
     * caller has to launch so the system installer UI opens on top of the caller.
     * The sender is only non-null when the status is AVAILABLE.
     */
    PendingInstallState installPendingUpdate(String packageName);
}
