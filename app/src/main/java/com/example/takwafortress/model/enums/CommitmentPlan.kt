package com.example.takwafortress.model.enums

enum class CommitmentPlan(
    val days: Int,
    val displayName: String,
    val description: String,
    val priceUSD: Float,
    // Replace each whopCheckoutUrl with your real Whop checkout link once you create plans in Whop dashboard
    val whopCheckoutUrl: String
) {
    TRIAL_3(
        days = 3,
        displayName = "3-Day Free Trial",
        description = "Try Taqwa Fortress completely free",
        priceUSD = 0.0f,
        whopCheckoutUrl = "https://whop.com/checkout/plan_2uycUylMHTZCN"   // free — no payment needed
    ),
    MONTHLY(
        days = 30,
        displayName = "1 Month",
        description = "Monthly protection plan",
        priceUSD = 3.9f,
        whopCheckoutUrl = "https://whop.com/checkout/plan_33t9naY9u9vSi"
    ),
    QUARTERLY(
        days = 90,
        displayName = "3 Months",
        description = "Quarterly protection plan",
        priceUSD = 9.9f,
        whopCheckoutUrl = "https://whop.com/checkout/plan_yAlqRYkaVVsSe"
    ),
    BIANNUAL(
        days = 180,
        displayName = "6 Months",
        description = "Semi-annual protection plan",
        priceUSD = 15.9f,
        whopCheckoutUrl = "https://whop.com/checkout/plan_aZwtLSbcWhqEV"
    ),
    ANNUAL(
        days = 365,
        displayName = "1 Year",
        description = "Full year protection plan",
        priceUSD = 24.9f,
        whopCheckoutUrl = "https://whop.com/checkout/plan_IzsKNB1gKPsOf"
    );

    val isFree: Boolean get() = priceUSD == 0.0f

    fun getPriceDisplay(): String = if (isFree) "FREE" else "$$priceUSD"

    fun getDurationMillis(): Long = days * 24L * 60L * 60L * 1000L

    companion object {
        fun fromDays(days: Int): CommitmentPlan? = values().find { it.days == days }
    }
}