package com.vincentmignot.nudgi.core.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/** Whether the user has enabled [NudgiAccessibilityService] from system settings. */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, NudgiAccessibilityService::class.java)
    val enabledServices =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
    }
    return false
}
