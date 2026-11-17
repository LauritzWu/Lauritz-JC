package io.nekohasekai.sagernet.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

object Theme {

    fun apply(context: Context) {
        context.setTheme(R.style.Theme_SagerNet_LauritzJC)
    }

    fun applyDialog(context: Context) {
        context.setTheme(R.style.Theme_SagerNet_Dialog_LauritzJC)
    }

    var currentNightMode = -1
    fun getNightMode(): Int {
        if (currentNightMode == -1) {
            currentNightMode = DataStore.nightTheme
        }
        return getNightMode(currentNightMode)
    }

    fun getNightMode(mode: Int): Int {
        return when (mode) {
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun applyNightTheme() {
        AppCompatDelegate.setDefaultNightMode(getNightMode())
    }

}
