package com.example.takwafortress.util.constants


object DnsServers {

    /**
     * CleanBrowsing Adult Filter (Primary DNS).
     * Blocks adult content only.
     */
    const val CLEANBROWSING_ADULT_FILTER = "adult-filter-dns.cleanbrowsing.org"

    /**
     * CleanBrowsing Family Filter.
     * Blocks adult content + social media + more.
     */
    const val CLEANBROWSING_FAMILY_FILTER = "family-filter-dns.cleanbrowsing.org"

    /**
     * CleanBrowsing Security Filter.
     * Blocks malware and phishing only.
     */
    const val CLEANBROWSING_SECURITY_FILTER = "security-filter-dns.cleanbrowsing.org"

    /**
     * Cloudflare for Families (Malware blocking).
     */
    const val CLOUDFLARE_MALWARE = "security.cloudflare-dns.com"

    /**
     * Cloudflare for Families (Malware + Adult content).
     */
    const val CLOUDFLARE_ADULT = "family.cloudflare-dns.com"

    /**
     * OpenDNS FamilyShield.
     */
    const val OPENDNS_FAMILY = "familyshield.opendns.com"

    /**
     * Quad9 (Malware blocking).
     */
    const val QUAD9 = "dns.quad9.net"

    /**
     * Gets DNS provider name.
     */
    fun getProviderName(dnsHostname: String): String {
        return when (dnsHostname) {
            CLEANBROWSING_ADULT_FILTER -> "CleanBrowsing Adult Filter"
            CLEANBROWSING_FAMILY_FILTER -> "CleanBrowsing Family Filter"
            CLEANBROWSING_SECURITY_FILTER -> "CleanBrowsing Security Filter"
            CLOUDFLARE_MALWARE -> "Cloudflare Malware Filter"
            CLOUDFLARE_ADULT -> "Cloudflare Adult Filter"
            OPENDNS_FAMILY -> "OpenDNS FamilyShield"
            QUAD9 -> "Quad9"
            else -> "Unknown DNS Provider"
        }
    }

    /**
     * Gets DNS provider description.
     */
    fun getProviderDescription(dnsHostname: String): String {
        return when (dnsHostname) {
            CLEANBROWSING_ADULT_FILTER -> "Blocks pornography and explicit content"
            CLEANBROWSING_FAMILY_FILTER -> "Blocks adult content, social media, and mixed content"
            CLEANBROWSING_SECURITY_FILTER -> "Blocks malware and phishing sites"
            CLOUDFLARE_MALWARE -> "Blocks malware and phishing"
            CLOUDFLARE_ADULT -> "Blocks malware and adult content"
            OPENDNS_FAMILY -> "Blocks adult content"
            QUAD9 -> "Blocks malware"
            else -> "Unknown DNS filter"
        }
    }

    /**
     * Checks if DNS is a content filter.
     */
    fun isContentFilter(dnsHostname: String): Boolean {
        return dnsHostname in setOf(
            CLEANBROWSING_ADULT_FILTER,
            CLEANBROWSING_FAMILY_FILTER,
            CLOUDFLARE_ADULT,
            OPENDNS_FAMILY
        )
    }
}