// The package matches the parcelable declared in PendingInstallState.aidl, which is
// what lets the Bazaar binder marshal this class.
package com.farsitel.bazaar

import android.content.IntentSender
import android.os.Parcel
import android.os.Parcelable

/**
 * Answer Bazaar gives to a pending-install query.
 *
 * [status] is kept as a raw number rather than an enum so that a newer Bazaar stays
 * readable to an older SDK and the other way around. Only Bazaar creates these.
 *
 * The field order is the wire format Bazaar writes, so it has to match
 * com.farsitel.bazaar.PendingInstallState in the Bazaar client exactly.
 */
internal class PendingInstallState(
    val status: Int,
    val packageName: String,
    val targetVersionCode: Long,
    val installedVersionCode: Long,
    val installIntentSender: IntentSender?,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(status)
        dest.writeString(packageName)
        dest.writeLong(targetVersionCode)
        dest.writeLong(installedVersionCode)
        dest.writeParcelable(installIntentSender, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PendingInstallState> {

        override fun createFromParcel(parcel: Parcel): PendingInstallState {
            return PendingInstallState(
                status = parcel.readInt(),
                packageName = parcel.readString().orEmpty(),
                targetVersionCode = parcel.readLong(),
                installedVersionCode = parcel.readLong(),
                installIntentSender = parcel.readParcelable(IntentSender::class.java.classLoader),
            )
        }

        override fun newArray(size: Int): Array<PendingInstallState?> = arrayOfNulls(size)
    }
}
