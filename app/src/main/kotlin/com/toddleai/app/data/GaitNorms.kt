package com.toddleai.app.data

data class CadenceRange(
    val low: Float,
    val high: Float,
    val source: String,
)

object GaitNorms {

    // Context-only cadence bands synthesized for parent-facing explanation from:
    // - Rygelova et al., PLOS ONE (2023)
    // - Sutherland developmental gait studies
    // - Dusing & Thorpe GAITRite pediatric norms
    // These are reference ranges for context only, not diagnostic thresholds.
    fun getCadenceRange(ageMonths: Int): CadenceRange = when {
        ageMonths < 18 -> CadenceRange(
            low = 150f,
            high = 190f,
            source = "Context from Rygelova et al. (PLOS ONE 2023), Sutherland, and Dusing & Thorpe GAITRite norms",
        )
        ageMonths < 24 -> CadenceRange(
            low = 135f,
            high = 175f,
            source = "Context from Rygelova et al. (PLOS ONE 2023), Sutherland, and Dusing & Thorpe GAITRite norms",
        )
        ageMonths < 36 -> CadenceRange(
            low = 120f,
            high = 160f,
            source = "Context from Rygelova et al. (PLOS ONE 2023), Sutherland, and Dusing & Thorpe GAITRite norms",
        )
        ageMonths < 48 -> CadenceRange(
            low = 115f,
            high = 145f,
            source = "Context from Rygelova et al. (PLOS ONE 2023), Sutherland, and Dusing & Thorpe GAITRite norms",
        )
        else -> CadenceRange(
            low = 110f,
            high = 135f,
            source = "Context from Rygelova et al. (PLOS ONE 2023), Sutherland, and Dusing & Thorpe GAITRite norms",
        )
    }

    // Developmental context only. This text is intended to help frame observations,
    // never to label a child as normal or abnormal.
    fun getWalkingMilestoneContext(ageMonths: Int): String = when {
        ageMonths < 18 ->
            "Most children walk independently between 9 and 18 months (WHO). Early walking is variable, and toddler gait is still rapidly developing."
        ageMonths < 48 ->
            "Most children walk independently between 9 and 18 months (WHO). Gait patterning becomes progressively more stable through the toddler and preschool years, with substantial maturation by about age 4 (Sutherland)."
        else ->
            "Most children walk independently between 9 and 18 months (WHO). Even after independent walking begins, gait continues to mature over early childhood, with substantial maturation by about age 4 (Sutherland)."
    }
}
