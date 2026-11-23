package com.eterion.glade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

data class LauncherApp(val component: ComponentName, val label: String, val icon: Drawable) {
    val id: String get() = component.flattenToString()
}

/** Called on the worker thread; one icon per launchable activity, retained for this catalog. */
fun loadApps(context: Context): List<LauncherApp> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val collator = Collator.getInstance(Locale.getDefault())
    val renderer = IconRenderer(context.resources, context.resources.getDimensionPixelSize(R.dimen.app_icon_size))
    @Suppress("DEPRECATION")
    return manager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.enabled }
        .mapNotNull { info ->
            try {
                LauncherApp(
                    ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                    info.loadLabel(manager).toString(), renderer.render(info.loadIcon(manager))
                )
            } catch (_: RuntimeException) {
                // An app can disappear while the package scan is in flight.
                null
            }
        }
        .distinctBy { it.id }
        .sortedWith { left, right -> collator.compare(left.label, right.label) }
}
