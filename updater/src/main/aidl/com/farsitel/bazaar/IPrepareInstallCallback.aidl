package com.farsitel.bazaar;

import android.app.PendingIntent;

oneway interface IPrepareInstallCallback {
    void onSuccess(in PendingIntent pendingIntent);
    void onError();
}
