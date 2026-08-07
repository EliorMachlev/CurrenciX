package de.salomax.helpers.changelog

// versionCode = MAJOR * 10_000 + MINOR * 100 + PATCH. Both changelog transformers
// (fastlane -> android resources and back) reconstruct semver from the versionCode
// with these multipliers, so they must stay in sync.
internal const val SEMVER_MAJOR_MULTIPLIER = 10_000
internal const val SEMVER_MINOR_MULTIPLIER = 100
