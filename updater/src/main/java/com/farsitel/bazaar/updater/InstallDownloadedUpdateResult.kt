package com.farsitel.bazaar.updater

public sealed class InstallDownloadedUpdateResult {

    public object Success : InstallDownloadedUpdateResult()

    public object Error : InstallDownloadedUpdateResult()
}
