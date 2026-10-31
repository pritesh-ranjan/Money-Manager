package com.moneymanager.app.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNormalizerTest {

    @Test
    fun testNormalize_railPrefixesAndReferenceIds() {
        assertEquals("netflix", MerchantNormalizer.normalize("UPI-NETFLIX.COM-998812341"))
        assertEquals("swiggy", MerchantNormalizer.normalize("POS SWIGGY 882319"))
        assertEquals("uber", MerchantNormalizer.normalize("NEFT-UBER INDIA-771239"))
        assertEquals("spotify", MerchantNormalizer.normalize("NACH/SPOTIFY INDIA PVT LTD"))
        assertEquals("amazon", MerchantNormalizer.normalize("ECOM/AMAZON PAY/123489"))
    }

    @Test
    fun testNormalize_corporateSuffixesAndDomains() {
        assertEquals("spotify", MerchantNormalizer.normalize("Spotify India Private Limited"))
        assertEquals("disney hotstar", MerchantNormalizer.normalize("www.hotstar.com"))
        assertEquals("github", MerchantNormalizer.normalize("GitHub Inc."))
    }

    @Test
    fun testNormalize_knownAliases() {
        assertEquals("amazon", MerchantNormalizer.normalize("amzn"))
        assertEquals("amazon", MerchantNormalizer.normalize("Amazon Retail India"))
        assertEquals("youtube premium", MerchantNormalizer.normalize("YT Premium"))
        assertEquals("disney hotstar", MerchantNormalizer.normalize("hotstar"))
    }

    @Test
    fun testSimilarity() {
        assertEquals(1.0, MerchantNormalizer.similarity("Netflix", "netflix"), 1e-9)
        assertTrue(MerchantNormalizer.similarity("Swiggy Instamart", "SWIGGY*ORDER 9923") >= 0.85)
        assertTrue(MerchantNormalizer.similarity("Uber Rides", "Zomato Limited") < 0.50)
    }

    @Test
    fun testClusterMerchants() {
        val merchants = setOf(
            "UPI-NETFLIX-99231",
            "Netflix.com",
            "NETFLIX INDIA",
            "Swiggy",
            "SWIGGY*ORDER 123"
        )
        val clusters = MerchantNormalizer.clusterMerchants(merchants, threshold = 0.85)

        val netflixClusters = merchants.filter { it.contains("NETFLIX", ignoreCase = true) }
            .map { clusters[it] }
            .distinct()
        assertEquals(1, netflixClusters.size)

        val swiggyClusters = merchants.filter { it.contains("Swiggy", ignoreCase = true) }
            .map { clusters[it] }
            .distinct()
        assertEquals(1, swiggyClusters.size)
    }
}
