package com.farsitel.bazaar.bazaarupdaterSample

import com.farsitel.bazaar.updater.AutoUpdateResult
import com.farsitel.bazaar.updater.PendingInstallLaunchResult
import com.farsitel.bazaar.updater.PendingInstallResult
import com.farsitel.bazaar.updater.UpdateResult

data class UpdateState(
    val updateResult: UpdateResult? = null,
    val autoUpdateResult: AutoUpdateResult? = null,
    val pendingInstallResult: PendingInstallResult? = null,
    val pendingInstallLaunchResult: PendingInstallLaunchResult? = null,
)
