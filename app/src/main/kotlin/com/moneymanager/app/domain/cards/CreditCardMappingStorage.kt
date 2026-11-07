package com.moneymanager.app.domain.cards

import android.content.Context
import org.json.JSONObject

/**
 * Persists mapping between statements/cards found via Gmail sync (or manual PDF import)
 * and existing user credit card accounts (AccountEntity.uid).
 *
 * Uses SharedPreferences with JSON serialization to ensure zero Room database schema
 * migration risk while offering instant persistence and reactive lookup.
 */
object CreditCardMappingStorage {
    private const val PREFS_NAME = "credit_card_mappings"
    private const val KEY_MAPPINGS_JSON = "card_to_account_mappings"

    /**
     * Builds a normalized canonical card key.
     * e.g. bankId="HDFC_SWIGGY_CC", cardMasked="526873XXXXXX1234" -> "hdfc_1234"
     */
    fun buildCardKey(bankId: String, cardMasked: String): String {
        val cleanMasked = cardMasked.replace("[^0-9]".toRegex(), "")
        val last4 = if (cleanMasked.length >= 4) cleanMasked.takeLast(4) else cardMasked.trim()
        val normalizedBank = bankId.lowercase()
            .replace("_cc", "")
            .replace("_bank", "")
            .replace("_credit_card", "")
            .replace("[^a-z0-9]".toRegex(), "")
            .take(12)
        return "${normalizedBank}_$last4"
    }

    /**
     * Returns all stored mappings: Map<CardKey, AccountUid>
     */
    fun getAllMappings(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_MAPPINGS_JSON, null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getString(key)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Resolves the mapped AccountEntity.uid for a given bank and masked card number.
     * Evaluates canonical key, raw card number, and last 4 digits for maximum resilience.
     */
    fun getMappedAccountUid(context: Context, bankId: String, cardMasked: String): String? {
        val mappings = getAllMappings(context)
        if (mappings.isEmpty()) return null

        val canonicalKey = buildCardKey(bankId, cardMasked)
        val directCard = cardMasked.replace(" ", "")
        val cleanDigits = cardMasked.replace("[^0-9]".toRegex(), "")
        val last4 = if (cleanDigits.length >= 4) cleanDigits.takeLast(4) else null

        return mappings[canonicalKey]
            ?: mappings[directCard]
            ?: (if (last4 != null) mappings[last4] else null)
            ?: mappings.entries.firstOrNull { (k, _) ->
                last4 != null && k.endsWith(last4)
            }?.value
    }

    /**
     * Associates a statement card with a user's credit card account UID.
     */
    fun saveCardMapping(context: Context, bankId: String, cardMasked: String, accountUid: String) {
        val mappings = getAllMappings(context).toMutableMap()
        val canonicalKey = buildCardKey(bankId, cardMasked)
        mappings[canonicalKey] = accountUid

        // Also save last 4 digits for resilient matching if no other card shares the last 4
        val cleanDigits = cardMasked.replace("[^0-9]".toRegex(), "")
        if (cleanDigits.length >= 4) {
            val last4 = cleanDigits.takeLast(4)
            mappings[last4] = accountUid
        }

        val json = JSONObject(mappings as Map<*, *>).toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAPPINGS_JSON, json)
            .apply()
    }

    /**
     * Removes the mapping for a statement card.
     */
    fun removeCardMapping(context: Context, bankId: String, cardMasked: String) {
        val mappings = getAllMappings(context).toMutableMap()
        val canonicalKey = buildCardKey(bankId, cardMasked)
        mappings.remove(canonicalKey)

        val cleanDigits = cardMasked.replace("[^0-9]".toRegex(), "")
        if (cleanDigits.length >= 4) {
            mappings.remove(cleanDigits.takeLast(4))
        }
        mappings.remove(cardMasked.replace(" ", ""))

        val json = JSONObject(mappings as Map<*, *>).toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAPPINGS_JSON, json)
            .apply()
    }
}
