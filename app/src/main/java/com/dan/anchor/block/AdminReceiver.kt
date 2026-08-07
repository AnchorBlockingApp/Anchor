package com.dan.anchor.block

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Registering as a device administrator is the one supported way an ordinary
 * app can refuse to be uninstalled. While this is active, Android greys out
 * Uninstall on the app info page and in the launcher.
 *
 * It isn't a lock — you can still deactivate it in Settings and then uninstall.
 * What it buys is that removing Anchor stops being a long-press on the icon and
 * becomes a deliberate trip through Settings, which the strict-mode guard then
 * blocks for the length of the cooldown.
 */
class AdminReceiver : DeviceAdminReceiver() {

    /** Shown by Android on the confirmation screen when someone tries to turn this off. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Anchor be uninstalled, which removes every block you've set."

    companion object {

        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun dpm(context: Context): DevicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        fun isActive(context: Context): Boolean =
            runCatching { dpm(context).isAdminActive(component(context)) }.getOrDefault(false)

        /** Opens the system screen that asks the user to grant admin. */
        fun enableIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Anchor uses this only to stop itself being uninstalled on impulse. " +
                        "It doesn't lock your screen, wipe anything, or change any other setting."
                )
            }

        fun disable(context: Context) {
            runCatching { dpm(context).removeActiveAdmin(component(context)) }
        }

        /**
         * Whether Android can actually see our receiver. If this is false the
         * approval screen will open and close instantly, which looks identical
         * to a silent failure — so it's worth being able to say which it is.
         */
        fun receiverVisible(context: Context): Boolean = runCatching {
            val intent = Intent(ACTION_DEVICE_ADMIN_ENABLED).setPackage(context.packageName)
            context.packageManager.queryBroadcastReceivers(intent, 0).any {
                it.activityInfo?.name == component(context).className
            }
        }.getOrDefault(false)

        /** Fallback route: the system list where admins can be switched on by hand. */
        fun settingsListIntent(): Intent =
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
    }
}
