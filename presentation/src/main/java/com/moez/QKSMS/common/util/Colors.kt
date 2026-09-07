/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.common.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.core.content.res.getColorOrThrow
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.getColorCompat
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.math.pow

@Singleton
class Colors @Inject constructor(
    private val context: Context,
    private val prefs: Preferences
) {

    val dynamicColorsSupported: Boolean
        get() = DynamicColors.isDynamicColorAvailable()

    fun applyDynamicColors(activity: Activity) {
        if (prefs.dynamicColors.get()) {
            DynamicColors.applyIfAvailable(activity, dynamicColorThemeOverlay())
        }
    }

    data class Theme(val theme: Int, private val colors: Colors) {
        val highlight by lazy { colors.highlightColorForTheme(theme) }
        val textPrimary by lazy { colors.textPrimaryOnThemeForColor(theme) }
        val textSecondary by lazy { colors.textSecondaryOnThemeForColor(theme) }
        val textTertiary by lazy { colors.textTertiaryOnThemeForColor(theme) }
    }

    data class WidgetPalette(
        val background: Int,
        val toolbar: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textTertiary: Int
    )

    private data class DynamicColorResolution(
        val dynamicContext: Context?,
        val isNight: Boolean
    )

    val materialColors: List<List<Int>> = listOf(
        R.array.material_red,
        R.array.material_pink,
        R.array.material_purple,
        R.array.material_deep_purple,
        R.array.material_indigo,
        R.array.material_blue,
        R.array.material_light_blue,
        R.array.material_cyan,
        R.array.material_teal,
        R.array.material_green,
        R.array.material_light_green,
        R.array.material_lime,
        R.array.material_yellow,
        R.array.material_amber,
        R.array.material_orange,
        R.array.material_deep_orange,
        R.array.material_brown,
        R.array.material_gray,
        R.array.material_blue_gray)
            .map { res -> context.resources.obtainTypedArray(res) }
            .map { typedArray -> (0 until typedArray.length()).map(typedArray::getColorOrThrow) }

    private val randomColors: List<Int> = context.resources.obtainTypedArray(R.array.random_colors)
            .let { typedArray -> (0 until typedArray.length()).map(typedArray::getColorOrThrow) }

    private val minimumContrastRatio = 2

    // Cache these values so they don't need to be recalculated
    private val primaryTextLuminance = measureLuminance(context.getColorCompat(R.color.textPrimaryDark))
    private val secondaryTextLuminance = measureLuminance(context.getColorCompat(R.color.textSecondaryDark))
    private val tertiaryTextLuminance = measureLuminance(context.getColorCompat(R.color.textTertiaryDark))

    fun theme(recipient: Recipient? = null): Theme {
        val pref = prefs.theme(recipient?.id ?: 0)
        val color = when {
            recipient == null || !prefs.autoColor.get() -> dynamicThemeColor() ?: pref.get()
            pref.isSet -> pref.get()
            else -> generateColor(recipient)
        }
        return Theme(color, this)
    }

    fun themeObservable(recipient: Recipient? = null): Observable<Theme> {
        val pref = when {
            recipient == null -> prefs.theme()
            prefs.autoColor.get() -> prefs.theme(recipient.id, generateColor(recipient))
            else -> prefs.theme(recipient.id, prefs.theme().get())
        }
        val colors = when {
            recipient == null || !prefs.autoColor.get() -> Observables.combineLatest(
                pref.asObservable(),
                prefs.dynamicColors.asObservable()
            ) { color, _ -> dynamicThemeColor() ?: color }
            else -> pref.asObservable()
        }
        return colors
                .map { color -> Theme(color, this) }
    }

    private fun dynamicThemeColor(): Int? {
        return dynamicColor(
            dynamicColorResolution(),
            com.google.android.material.R.attr.colorPrimary
        )
    }

    fun widgetPalette(): WidgetPalette {
        val resolution = dynamicColorResolution()
        val isNight = resolution.isNight
        val black = prefs.black.get()
        val background = resolveColor(
            resolution,
            com.google.android.material.R.attr.colorSurface,
            if (isNight) R.color.backgroundDark else R.color.backgroundLight
        ).takeUnless { isNight && black } ?: context.getColorCompat(R.color.black)
        val textOnSurfaceVariant = dynamicColor(
            resolution,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        return WidgetPalette(
            background = background,
            toolbar = background,
            textPrimary = resolveColor(
                resolution,
                com.google.android.material.R.attr.colorOnSurface,
                if (isNight) R.color.textPrimaryDark else R.color.textPrimary
            ),
            textSecondary = textOnSurfaceVariant ?: context.getColorCompat(
                if (isNight) R.color.textSecondaryDark else R.color.textSecondary
            ),
            textTertiary = textOnSurfaceVariant ?: context.getColorCompat(
                if (isNight) R.color.textTertiaryDark else R.color.textTertiary
            )
        )
    }

    private fun dynamicColor(
        resolution: DynamicColorResolution,
        attribute: Int
    ): Int? {
        return resolution.dynamicContext?.let { dynamicContext ->
            MaterialColors.getColor(dynamicContext, attribute, 0)
                .takeIf { color -> color != 0 }
        }
    }

    private fun resolveColor(
        resolution: DynamicColorResolution,
        attribute: Int,
        fallback: Int
    ): Int {
        return dynamicColor(resolution, attribute) ?: context.getColorCompat(fallback)
    }

    private fun dynamicColorResolution(): DynamicColorResolution {
        val isNight = prefs.night.get() ||
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        if (!dynamicColorsSupported || !prefs.dynamicColors.get()) {
            return DynamicColorResolution(null, isNight)
        }

        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val black = prefs.black.get()
        val baseTheme = if (black) R.style.AppTheme_Black else R.style.AppTheme
        val baseContext = ContextThemeWrapper(context.createConfigurationContext(configuration), baseTheme)
        val dynamicContext = DynamicColors.wrapContextIfAvailable(
            baseContext,
            dynamicColorThemeOverlay()
        )
        return DynamicColorResolution(dynamicContext, isNight)
    }

    private fun dynamicColorThemeOverlay(): Int {
        return if (prefs.black.get()) {
            R.style.ThemeOverlay_Quik_DynamicColors_Black
        } else {
            R.style.ThemeOverlay_Quik_DynamicColors
        }
    }

    fun highlightColorForTheme(theme: Int): Int = FloatArray(3)
            .apply { Color.colorToHSV(theme, this) }
            .let { hsv -> hsv.apply { set(2, 0.75f) } } // 75% value
            .let { hsv -> Color.HSVToColor(85, hsv) } // 33% alpha

    fun textPrimaryOnThemeForColor(color: Int): Int = color
            .let { theme -> measureLuminance(theme) }
            .let { themeLuminance -> primaryTextLuminance / themeLuminance }
            .let { contrastRatio -> contrastRatio < minimumContrastRatio }
            .let { contrastRatio -> if (contrastRatio) R.color.textPrimary else R.color.textPrimaryDark }
            .let { res -> context.getColorCompat(res) }

    fun textSecondaryOnThemeForColor(color: Int): Int = color
            .let { theme -> measureLuminance(theme) }
            .let { themeLuminance -> secondaryTextLuminance / themeLuminance }
            .let { contrastRatio -> contrastRatio < minimumContrastRatio }
            .let { contrastRatio -> if (contrastRatio) R.color.textSecondary else R.color.textSecondaryDark }
            .let { res -> context.getColorCompat(res) }

    fun textTertiaryOnThemeForColor(color: Int): Int = color
            .let { theme -> measureLuminance(theme) }
            .let { themeLuminance -> tertiaryTextLuminance / themeLuminance }
            .let { contrastRatio -> contrastRatio < minimumContrastRatio }
            .let { contrastRatio -> if (contrastRatio) R.color.textTertiary else R.color.textTertiaryDark }
            .let { res -> context.getColorCompat(res) }

    /**
     * Measures the luminance value of a color to be able to measure the contrast ratio between two materialColors
     * Based on https://stackoverflow.com/a/9733420
     */
    private fun measureLuminance(color: Int): Double {
        val array = intArrayOf(Color.red(color), Color.green(color), Color.blue(color))
            .map { it / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

        return 0.2126 * array[0] + 0.7152 * array[1] + 0.0722 * array[2] + 0.05
    }

    private fun generateColor(recipient: Recipient): Int {
        val index = recipient.address.hashCode().absoluteValue % randomColors.size
        return randomColors[index]
    }
}
