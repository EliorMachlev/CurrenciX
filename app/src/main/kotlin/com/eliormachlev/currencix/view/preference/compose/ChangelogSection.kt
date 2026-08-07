package com.eliormachlev.currencix.view.preference.compose

import android.content.Context
import android.content.res.Resources
import com.eliormachlev.currencix.R
import java.lang.reflect.Modifier

private const val SEMVER_MAJOR_MULTIPLIER = 10_000
private const val SEMVER_MINOR_MULTIPLIER = 100
private const val CHANGELOG_ARRAY_PREFIX = "changelog_"

data class ChangelogSection(
    val version: String,
    val bullets: List<CharSequence>,
)

// HINT: needs proguard rule to work in release config — see proguard-rules.pro
internal fun loadChangelogSections(context: Context): List<ChangelogSection> {
    val resources: Resources = context.resources
    return R.array::class.java.declaredFields
        .asSequence()
        .filter { it.name.startsWith(CHANGELOG_ARRAY_PREFIX) }
        .filter { field ->
            val m = field.modifiers
            Modifier.isStatic(m) &&
                !Modifier.isPrivate(m) &&
                field.type == Int::class.java
        }.sortedByDescending { field -> semverWeight(field.name) }
        .mapNotNull { field ->
            try {
                val arrayId = field.getInt(null)
                val version = field.name.substringAfter('_').replace('_', '.')
                val bullets = resources.getTextArray(arrayId).toList()
                ChangelogSection(version = version, bullets = bullets)
            } catch (_: IllegalAccessException) {
                null
            }
        }.toList()
}

private fun semverWeight(fieldName: String): Int {
    val parts = fieldName.substringAfter('_').split('_')
    return try {
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        val patch = parts[2].toInt()
        major * SEMVER_MAJOR_MULTIPLIER + minor * SEMVER_MINOR_MULTIPLIER + patch
    } catch (_: NumberFormatException) {
        0
    } catch (_: IndexOutOfBoundsException) {
        0
    }
}
