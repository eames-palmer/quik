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
package dev.octoshrimpy.quik.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.common.util.extensions.withAlpha
import dev.octoshrimpy.quik.injection.appComponent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import javax.inject.Inject

class QkSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : MaterialSwitch(context, attrs) {

    @Inject lateinit var colors: Colors

    private var themeDisposable: Disposable? = null

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!isInEditMode) {
            themeDisposable?.dispose()
            themeDisposable = colors.themeObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { theme -> updateThemeColors(theme.theme) }
        }
    }

    override fun onDetachedFromWindow() {
        themeDisposable?.dispose()
        themeDisposable = null
        super.onDetachedFromWindow()
    }

    private fun updateThemeColors(themeColor: Int) {
        val states = arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf())

        thumbTintList = ColorStateList(states, intArrayOf(
                context.resolveThemeColor(R.attr.switchThumbDisabled),
                themeColor,
                context.resolveThemeColor(R.attr.switchThumbEnabled)))

        trackTintList = ColorStateList(states, intArrayOf(
                context.resolveThemeColor(R.attr.switchTrackDisabled),
                themeColor.withAlpha(0x4D),
                context.resolveThemeColor(R.attr.switchTrackEnabled)))
    }
}