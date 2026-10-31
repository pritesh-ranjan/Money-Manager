package com.moneymanager.app.domain.insights

import kotlin.math.max
import kotlin.math.min

object MerchantNormalizer {

    private val PREFIX_PATTERNS = listOf(
        Regex("^(upi[-/:]?|pos[-/ ]+|ach[-/ ]+|nach[-/ ]+|neft[-/ ]+|imps[-/ ]+|ecom[-/ ]+)", RegexOption.IGNORE_CASE),
        Regex("^(razorpay\\*?|paytm\\*?|phonepe\\*?|gpay\\*?|billdesk\\*?|ccavenue\\*?)", RegexOption.IGNORE_CASE),
        Regex("^www\\.", RegexOption.IGNORE_CASE)
    )

    private val DOMAIN_PATTERNS = listOf(
        Regex("\\.(com|in|co\\.in|org|net|io|app|co)([-/\\s]|$)", RegexOption.IGNORE_CASE)
    )

    private val SUFFIX_PATTERNS = listOf(
        Regex("\\b(pvt\\s*ltd|private\\s+limited|ltd|inc|llc|corp|corporation|services|technologies|solutions|india)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b[0-9]{4,}\\b") // Strip order / reference IDs of 4 or more digits
    )

    private val KNOWN_ALIASES = mapOf(
        "amzn" to "amazon",
        "amazon pay" to "amazon",
        "amazon retail" to "amazon",
        "amazon in" to "amazon",
        "yt premium" to "youtube premium",
        "youtube" to "youtube premium",
        "hotstar" to "disney hotstar",
        "spotify ab" to "spotify",
        "apple com bill" to "apple",
        "apple services" to "apple",
        "google storage" to "google one",
        "google play" to "google",
        "swiggy instamart" to "swiggy",
        "swiggy genies" to "swiggy",
        "zomato limited" to "zomato"
    )

    /**
     * Normalizes a raw payee / note into a clean canonical identifier.
     * E.g. "UPI-NETFLIX.COM-998812341" -> "netflix"
     */
    fun normalize(raw: String): String {
        var text = raw.trim().lowercase()
        if (text.isEmpty()) return ""

        // Strip rail prefixes
        for (prefix in PREFIX_PATTERNS) {
            text = text.replace(prefix, "")
        }

        // Strip domain extensions
        for (domain in DOMAIN_PATTERNS) {
            text = text.replace(domain, " ")
        }

        // Strip corporate suffixes and long numeric codes
        for (suffix in SUFFIX_PATTERNS) {
            text = text.replace(suffix, " ")
        }

        // Remove special punctuation, keep letters, digits, and spaces
        text = text.replace(Regex("[^a-z0-9\\s]"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()

        // Alias canonicalization
        KNOWN_ALIASES[text]?.let { return it }
        for ((alias, canonical) in KNOWN_ALIASES) {
            if (text == alias || text.startsWith("$alias ")) {
                return canonical
            }
        }

        return text
    }

    /**
     * Computes similarity between two raw or normalized merchant names [0.0..1.0].
     * Uses the maximum of Jaro-Winkler similarity and Token Jaccard similarity.
     */
    fun similarity(rawA: String, rawB: String): Double {
        val a = normalize(rawA)
        val b = normalize(rawB)

        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        val jw = jaroWinkler(a, b)
        val jaccard = tokenJaccard(a, b)

        return max(jw, jaccard)
    }

    /**
     * Clusters a set of merchant names into canonical representatives.
     * Returns a map of rawName -> canonicalName.
     */
    fun clusterMerchants(merchants: Set<String>, threshold: Double = 0.85): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val canonicals = mutableListOf<String>()

        for (merchant in merchants) {
            val norm = normalize(merchant)
            if (norm.isEmpty()) {
                result[merchant] = merchant
                continue
            }

            var matchedCanonical: String? = null
            for (can in canonicals) {
                if (similarity(norm, can) >= threshold) {
                    matchedCanonical = can
                    break
                }
            }

            if (matchedCanonical != null) {
                result[merchant] = matchedCanonical
            } else {
                canonicals.add(norm)
                result[merchant] = norm
            }
        }

        return result
    }

    // --- Private String Distance Utilities ---

    private fun jaroWinkler(s1: String, s2: String, p: Double = 0.1): Double {
        val jaro = jaro(s1, s2)
        if (jaro < 0.7) return jaro

        var prefix = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        while (prefix < maxPrefix && s1[prefix] == s2[prefix]) {
            prefix++
        }
        return jaro + prefix * p * (1.0 - jaro)
    }

    private fun jaro(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (!s2Matches[j] && s1[i] == s2[j]) {
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) {
                k++
            }
            if (s1[i] != s2[k]) {
                transpositions++
            }
            k++
        }

        return (
            matches.toDouble() / len1 +
            matches.toDouble() / len2 +
            (matches - transpositions / 2.0) / matches
        ) / 3.0
    }

    private fun tokenJaccard(s1: String, s2: String): Double {
        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()
        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return intersection.toDouble() / union
    }
}
