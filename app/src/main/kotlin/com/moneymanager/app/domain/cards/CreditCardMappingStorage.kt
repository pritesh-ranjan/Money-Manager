package com.moneymanager.app.domain.cards

import android.content.Context
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.InstitutionEntity
import com.moneymanager.app.data.local.entity.InstitutionKind
import com.moneymanager.app.data.local.entity.resolvedInstitutionName
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

    fun extractLast4Digits(raw: String?): String? {
        val digits = raw?.replace(Regex("[^0-9]"), "") ?: return null
        return digits.takeLast(4).takeIf { it.length == 4 }
    }

    private fun bankNameMatches(candidateName: String?, statementBankName: String): Boolean {
        val candidate = candidateName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        val stmtLower = statementBankName.trim().lowercase()
        if (stmtLower.isEmpty()) return false
        if (candidate == stmtLower || stmtLower.contains(candidate) || candidate.contains(stmtLower)) return true
        val stopWords = setOf("bank", "card", "credit", "ltd", "limited", "india", "the")
        val stmtTokens = stmtLower.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in stopWords }
        val candTokens = candidate.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in stopWords }
        return stmtTokens.isNotEmpty() && candTokens.any { it in stmtTokens }
    }

    fun findMatchingBankInstitution(
        statementBankName: String,
        banks: List<InstitutionEntity>
    ): InstitutionEntity? {
        val cleanTarget = statementBankName.trim()
        if (cleanTarget.isEmpty()) return null
        val activeBanks = banks.filter { !it.isDeleted && it.kind == InstitutionKind.BANK }
        return activeBanks.firstOrNull { it.name.trim().equals(cleanTarget, ignoreCase = true) }
            ?: activeBanks.firstOrNull { bankNameMatches(it.name, cleanTarget) }
    }

    fun resolveCardAccount(
        mappedAccountUid: String?,
        statementBankName: String,
        cardMasked: String,
        accounts: List<AccountEntity>,
        institutionsMap: Map<String, InstitutionEntity> = emptyMap()
    ): AccountEntity? {
        val activeCards = accounts.filter { !it.isArchived && it.accountType == AccountType.CREDIT_CARD }
        if (activeCards.isEmpty()) return null

        if (!mappedAccountUid.isNullOrBlank()) {
            activeCards.firstOrNull { it.uid == mappedAccountUid }?.let { return it }
        }

        val stmtLast4 = extractLast4Digits(cardMasked)
        fun matchesBankForAccount(acc: AccountEntity): Boolean {
            val instName = acc.resolvedInstitutionName(institutionsMap)
            return bankNameMatches(instName, statementBankName) || bankNameMatches(acc.name, statementBankName)
        }

        if (stmtLast4 != null) {
            val numberMatches = activeCards.filter { extractLast4Digits(it.accountNumber) == stmtLast4 }
            if (numberMatches.size == 1) return numberMatches.first()
            if (numberMatches.size > 1) {
                return numberMatches.firstOrNull { matchesBankForAccount(it) } ?: numberMatches.first()
            }

            val nameDigitMatches = activeCards.filter { it.name.contains(stmtLast4) }
            if (nameDigitMatches.size == 1) return nameDigitMatches.first()
            if (nameDigitMatches.size > 1) {
                return nameDigitMatches.firstOrNull { matchesBankForAccount(it) } ?: nameDigitMatches.first()
            }
        }

        val bankCandidates = activeCards.filter { acc ->
            val accLast4 = extractLast4Digits(acc.accountNumber)
            val hasConflictingDigits = stmtLast4 != null && accLast4 != null && accLast4 != stmtLast4
            !hasConflictingDigits && matchesBankForAccount(acc)
        }
        return if (bankCandidates.size == 1) bankCandidates.first() else null
    }
}
