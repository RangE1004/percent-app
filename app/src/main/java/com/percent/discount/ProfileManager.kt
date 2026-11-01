package com.percent.discount


import android.content.Context
import android.content.SharedPreferences

object ProfileManager {
    private const val PREFS_NAME = "PercentAppProfile"
    private const val KEY_CARD = "user_card"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 카드사 정보 저장
    fun saveCard(context: Context, cardName: String) {
        getPrefs(context).edit().putString(KEY_CARD, cardName).apply()
    }

    // 카드사 정보 불러오기
    fun getCard(context: Context): String {
        return getPrefs(context).getString(KEY_CARD, "등록된 카드 없음") ?: "등록된 카드 없음"
    }
}
