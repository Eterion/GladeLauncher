package com.eterion.glade

data class DrawerGroups(val quick: List<String>, val alphabetical: List<String>)

object QuickAccess {
    fun normalize(ids: List<String>): List<String> = ids.filter { it.isNotBlank() }.distinct()

    fun add(ids: List<String>, id: String): List<String> = normalize(ids + id)

    fun remove(ids: List<String>, id: String): List<String> = normalize(ids).filterNot { it == id }

    fun removePackage(ids: List<String>, packageName: String): List<String> =
        normalize(ids).filterNot { it.substringBefore('/') == packageName }

    fun group(available: List<String>, pinned: List<String>): DrawerGroups {
        val apps = normalize(available)
        val availableSet = apps.toSet()
        val quick = normalize(pinned).filter { it in availableSet }
        val quickSet = quick.toSet()
        return DrawerGroups(quick, apps.filterNot { it in quickSet })
    }
}
