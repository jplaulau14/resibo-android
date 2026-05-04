package com.patslaurel.resibo.factcheck

data class FactCheckResult(
    val claimText: String,
    val claimant: String,
    val rating: String,
    val reviewUrl: String,
    val reviewTitle: String,
    val publisherName: String,
    val publisherSite: String,
    val reviewDate: String,
)
