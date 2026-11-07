package com.moneymanager.app.domain.gmail.filter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GmailSyncRules(
    val baseSearchQuery: String = DEFAULT_SEARCH_QUERY,
    val enabledBanks: Set<String> = SupportedBank.entries.map { it.name }.toSet(),
    val customBankKeywords: List<String> = emptyList(),
    val enabledCardParserIds: Set<String> = setOf("ICICI_AMAZON_PAY_CC", "HDFC_SWIGGY_CC", "AXIS_BANK_CC"),
    val supportedCardKeywords: List<String> = emptyList(),
    val ignoredSubjectKeywords: List<String> = DEFAULT_IGNORED_SUBJECTS
) {
    companion object {
        const val DEFAULT_SEARCH_QUERY = "has:attachment filename:pdf from:bank.in"
        val DEFAULT_IGNORED_SUBJECTS = listOf(
            "Combined",
            "Savings Account",
            "Smart Statement",
            "Monthly Account Statement"
        )
    }
}

object GmailSyncRulesStorage {
    private const val PREFS_NAME = "gmail_sync_rules"
    private const val KEY_RULES_JSON = "sync_rules_config"

    fun getRules(context: Context): GmailSyncRules {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RULES_JSON, null) ?: return GmailSyncRules()
        return try {
            val json = JSONObject(jsonStr)
            val baseQuery = json.optString("baseSearchQuery", GmailSyncRules.DEFAULT_SEARCH_QUERY)

            val enabledBanks = mutableSetOf<String>()
            val banksArr = json.optJSONArray("enabledBanks")
            if (banksArr != null) {
                for (i in 0 until banksArr.length()) {
                    enabledBanks.add(banksArr.getString(i))
                }
            } else {
                enabledBanks.addAll(SupportedBank.entries.map { it.name })
            }

            val customBankKeywords = mutableListOf<String>()
            val customBanksArr = json.optJSONArray("customBankKeywords")
            if (customBanksArr != null) {
                for (i in 0 until customBanksArr.length()) {
                    customBankKeywords.add(customBanksArr.getString(i))
                }
            }

            val enabledCardParserIds = mutableSetOf<String>()
            val cardsArr = json.optJSONArray("enabledCardParserIds")
            if (cardsArr != null) {
                for (i in 0 until cardsArr.length()) {
                    enabledCardParserIds.add(cardsArr.getString(i))
                }
            } else {
                enabledCardParserIds.addAll(listOf("ICICI_AMAZON_PAY_CC", "HDFC_SWIGGY_CC", "AXIS_BANK_CC"))
            }

            val supportedCardKeywords = mutableListOf<String>()
            val cardKeywordsArr = json.optJSONArray("supportedCardKeywords")
            if (cardKeywordsArr != null) {
                for (i in 0 until cardKeywordsArr.length()) {
                    supportedCardKeywords.add(cardKeywordsArr.getString(i))
                }
            }

            val ignoredSubjectKeywords = mutableListOf<String>()
            val ignoredArr = json.optJSONArray("ignoredSubjectKeywords")
            if (ignoredArr != null) {
                for (i in 0 until ignoredArr.length()) {
                    ignoredSubjectKeywords.add(ignoredArr.getString(i))
                }
            } else {
                ignoredSubjectKeywords.addAll(GmailSyncRules.DEFAULT_IGNORED_SUBJECTS)
            }

            GmailSyncRules(
                baseSearchQuery = baseQuery,
                enabledBanks = enabledBanks,
                customBankKeywords = customBankKeywords,
                enabledCardParserIds = enabledCardParserIds,
                supportedCardKeywords = supportedCardKeywords,
                ignoredSubjectKeywords = ignoredSubjectKeywords
            )
        } catch (_: Exception) {
            GmailSyncRules()
        }
    }

    fun saveRules(context: Context, rules: GmailSyncRules) {
        val json = JSONObject().apply {
            put("baseSearchQuery", rules.baseSearchQuery)
            put("enabledBanks", JSONArray(rules.enabledBanks.toList()))
            put("customBankKeywords", JSONArray(rules.customBankKeywords))
            put("enabledCardParserIds", JSONArray(rules.enabledCardParserIds.toList()))
            put("supportedCardKeywords", JSONArray(rules.supportedCardKeywords))
            put("ignoredSubjectKeywords", JSONArray(rules.ignoredSubjectKeywords))
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES_JSON, json.toString())
            .apply()
    }

    fun addCustomBank(context: Context, bankName: String): GmailSyncRules {
        val clean = bankName.trim()
        val current = getRules(context)
        if (clean.isBlank() || current.customBankKeywords.any { it.equals(clean, ignoreCase = true) }) {
            return current
        }
        val updated = current.copy(
            customBankKeywords = current.customBankKeywords + clean
        )
        saveRules(context, updated)
        return updated
    }

    fun removeCustomBank(context: Context, bankName: String): GmailSyncRules {
        val current = getRules(context)
        val updated = current.copy(
            customBankKeywords = current.customBankKeywords.filterNot { it.equals(bankName.trim(), ignoreCase = true) }
        )
        saveRules(context, updated)
        return updated
    }

    fun addIgnoredSubject(context: Context, keyword: String): GmailSyncRules {
        val clean = keyword.trim()
        val current = getRules(context)
        if (clean.isBlank() || current.ignoredSubjectKeywords.any { it.equals(clean, ignoreCase = true) }) {
            return current
        }
        val updated = current.copy(
            ignoredSubjectKeywords = current.ignoredSubjectKeywords + clean
        )
        saveRules(context, updated)
        return updated
    }

    fun removeIgnoredSubject(context: Context, keyword: String): GmailSyncRules {
        val current = getRules(context)
        val updated = current.copy(
            ignoredSubjectKeywords = current.ignoredSubjectKeywords.filterNot { it.equals(keyword.trim(), ignoreCase = true) }
        )
        saveRules(context, updated)
        return updated
    }
}
