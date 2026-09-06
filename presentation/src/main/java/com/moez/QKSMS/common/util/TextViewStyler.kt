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

import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.TextView
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.TextViewStyler.Companion.SIZE_PRIMARY
import dev.octoshrimpy.quik.common.util.TextViewStyler.Companion.SIZE_SECONDARY
import dev.octoshrimpy.quik.common.util.TextViewStyler.Companion.SIZE_TERTIARY
import dev.octoshrimpy.quik.common.util.TextViewStyler.Companion.SIZE_TOOLBAR
import dev.octoshrimpy.quik.common.util.TextViewStyler.Companion.SIZE_DIALOG
import dev.octoshrimpy.quik.common.util.TextViewStyler.Companion.SIZE_EMOJI
import dev.octoshrimpy.quik.common.util.extensions.getColorCompat
import dev.octoshrimpy.quik.common.widget.QkEditText
import dev.octoshrimpy.quik.common.widget.QkTextView
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import javax.inject.Inject

private data class TextViewAttributes(
    val color: Int,
    val textSize: Int
)

private val textSizePresets = mapOf(
    SIZE_PRIMARY to floatArrayOf(14f, 16f, 18f, 20f, 40f),
    SIZE_SECONDARY to floatArrayOf(12f, 14f, 16f, 18f, 36f),
    SIZE_TERTIARY to floatArrayOf(10f, 12f, 14f, 16f, 32f),
    SIZE_TOOLBAR to floatArrayOf(18f, 20f, 22f, 26f, 52f),
    SIZE_DIALOG to floatArrayOf(16f, 18f, 20f, 24f, 48f),
    SIZE_EMOJI to floatArrayOf(28f, 32f, 36f, 40f, 80f)
)

private fun readTextViewAttributes(
    textView: TextView,
    attrs: AttributeSet?
): TextViewAttributes? {
    val (styleable, colorAttribute, textSizeAttribute) = when (textView) {
        is QkTextView -> Triple(
            R.styleable.QkTextView,
            R.styleable.QkTextView_textColor,
            R.styleable.QkTextView_textSize
        )

        is QkEditText -> Triple(
            R.styleable.QkEditText,
            R.styleable.QkEditText_textColor,
            R.styleable.QkEditText_textSize
        )

        else -> return null
    }

    return textView.context.obtainStyledAttributes(attrs, styleable).run {
        try {
            TextViewAttributes(
                color = getInt(colorAttribute, -1),
                textSize = getInt(textSizeAttribute, -1)
            )
        } finally {
            recycle()
        }
    }
}

private fun textSizeFor(textSizeAttribute: Int, textSizePreference: Int): Float? {
    return textSizePresets[textSizeAttribute]?.let { sizes ->
        sizes.getOrNull(textSizePreference) ?: sizes[Preferences.TEXT_SIZE_NORMAL]
    }
}


class TextViewStyler @Inject constructor(
    private val prefs: Preferences,
    private val colors: Colors,
    private val fontProvider: FontProvider
) {

    companion object {
        const val COLOR_THEME = 0
        const val COLOR_PRIMARY_ON_THEME = 1
        const val COLOR_SECONDARY_ON_THEME = 2
        const val COLOR_TERTIARY_ON_THEME = 3

        const val SIZE_PRIMARY = 0
        const val SIZE_SECONDARY = 1
        const val SIZE_TERTIARY = 2
        const val SIZE_TOOLBAR = 3
        const val SIZE_DIALOG = 4
        const val SIZE_EMOJI = 5

        fun applyEditModeAttributes(textView: TextView, attrs: AttributeSet?) {
            val attributes = readTextViewAttributes(textView, attrs) ?: return

            textView.setTextColor(when (attributes.color) {
                COLOR_PRIMARY_ON_THEME -> textView.context.getColorCompat(R.color.textPrimaryDark)
                COLOR_SECONDARY_ON_THEME -> textView.context.getColorCompat(R.color.textSecondaryDark)
                COLOR_TERTIARY_ON_THEME -> textView.context.getColorCompat(R.color.textTertiaryDark)
                COLOR_THEME -> textView.context.getColorCompat(R.color.tools_theme)
                else -> textView.currentTextColor
            })

            textView.textSize = textSizeFor(
                attributes.textSize,
                Preferences.TEXT_SIZE_NORMAL
            ) ?: textView.textSize / textView.paint.density
        }
    }

    fun applyAttributes(textView: TextView, attrs: AttributeSet?) {
        if (!prefs.systemFont.get()) {
            fontProvider.getLato { lato ->
                textView.setTypeface(lato, textView.typeface?.style ?: Typeface.NORMAL)
            }
        }

        val attributes = readTextViewAttributes(textView, attrs) ?: return

        if (attributes.color in COLOR_THEME..COLOR_TERTIARY_ON_THEME || textView is EditText) {
            observeThemeColors(textView, attributes.color)
        }

        setTextSize(textView, attributes.textSize)
    }

    private fun observeThemeColors(textView: TextView, colorAttr: Int) {
        val applyTheme = { theme: Colors.Theme ->
            when (colorAttr) {
                COLOR_THEME -> textView.setTextColor(theme.theme)
                COLOR_PRIMARY_ON_THEME -> textView.setTextColor(theme.textPrimary)
                COLOR_SECONDARY_ON_THEME -> textView.setTextColor(theme.textSecondary)
                COLOR_TERTIARY_ON_THEME -> textView.setTextColor(theme.textTertiary)
            }

            if (textView is EditText && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textView.textCursorDrawable = textView.resources.getDrawable(R.drawable.cursor)
                        .apply { setTint(theme.theme) }
            }
        }

        val themeObserver = object : View.OnAttachStateChangeListener {
            private var disposable: Disposable? = null

            override fun onViewAttachedToWindow(view: View) {
                disposable?.dispose()
                disposable = colors.themeObservable()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(applyTheme)
            }

            override fun onViewDetachedFromWindow(view: View) {
                disposable?.dispose()
                disposable = null
            }
        }

        textView.addOnAttachStateChangeListener(themeObserver)
        if (textView.isAttachedToWindow) {
            themeObserver.onViewAttachedToWindow(textView)
        } else {
            applyTheme(colors.theme())
        }
    }

    /**
     * @see SIZE_PRIMARY
     * @see SIZE_SECONDARY
     * @see SIZE_TERTIARY
     * @see SIZE_TOOLBAR
     */
    fun setTextSize(textView: TextView, textSizeAttr: Int) {
        textSizeFor(textSizeAttr, prefs.textSize.get())?.let { textView.textSize = it }
    }

}