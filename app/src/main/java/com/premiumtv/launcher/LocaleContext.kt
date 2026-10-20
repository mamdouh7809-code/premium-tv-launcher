package com.premiumtv.launcher

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleContext {
    fun wrap(context: Context): Context {
        val tag = context.getSharedPreferences(Preferences.FILE, Context.MODE_PRIVATE).getString("language", "auto")
        if (tag != "ar" && tag != "en") return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
