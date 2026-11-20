package io.nekohasekai.sagernet.ui

internal object ReleaseUpdate {
    private val legacyArm64Apks = listOf(
        "veyr-preview-arm64-v8a.apk",
        "lauritz-jc-preview-arm64-v8a.apk"
    )
    private val releaseTag = Regex("""v(\d+\.\d+\.\d+-(?:beta\.\d+|preview|stable))\.(\d+)""")

    data class Candidate(val code: Long, val apkName: String)

    fun candidate(
        tag: String,
        draft: Boolean,
        prerelease: Boolean,
        includeBeta: Boolean,
        currentBest: Long,
        uploadedNames: () -> List<String>
    ): Candidate? {
        if (draft || (!includeBeta && prerelease)) return null
        val match = releaseTag.matchEntire(tag) ?: return null
        val code = match.groupValues[2].toLongOrNull() ?: return null
        if (code <= currentBest) return null
        val releaseVersion = match.groupValues[1]
        val names = uploadedNames()
        val apkName = "veyr-$releaseVersion-arm64-v8a.apk".takeIf { it in names }
            ?: "veyr-$releaseVersion-candidate-arm64-v8a.apk"
                .takeIf { "-beta." in releaseVersion && it in names }
            ?: legacyArm64Apks.firstOrNull { it in names }
            ?: return null
        return Candidate(code, apkName)
    }

    fun isUpgrade(available: Long, installed: Long): Boolean = available > installed
}
