package com.farsitel.bazaar;
interface IUpdateCheckService {
    long getVersionCode(String packageName);
    long getRemoteVersionCode(String packageName);

    /**
     * True when Bazaar holds a completed download for [packageName] whose version is
     * newer than the one installed on the device, so the update was downloaded but
     * never installed. Only the app that owns the package can ask about it: the caller
     * is identified by the uid behind the package name it passes.
     *
     * The caller finishes that download through Bazaar's own app page, which already
     * installs a ready download when the user taps install there.
     */
    boolean isUpdateDownloaded(String packageName);
}
