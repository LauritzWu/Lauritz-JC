package io.nekohasekai.sagernet.database

import android.os.Parcelable
import androidx.room.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import kotlinx.parcelize.Parcelize

@Entity(tableName = "rules")
@Parcelize
@TypeConverters(StringCollectionConverter::class)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "",
    @ColumnInfo(defaultValue = "")
    var config: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = false,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: String = "",
    var source: String = "",
    var protocol: String = "",
    @ColumnInfo(name = "ruleset", defaultValue = "")
    var ruleset: String = "",
    var outbound: Long = 0,
    var packages: Set<String> = emptySet(),
) : Parcelable {

    fun displayName(): String {
        val match = copy(id = 0L, name = "", userOrder = 0L, enabled = false)
        return when {
            match == RuleEntity(port = "443", network = "udp", outbound = -2) &&
                name in setOf("Block UDP 443", "阻断 UDP 443", "阻擋 UDP 443") ->
                app.getString(R.string.route_builtin_block_udp_443)
            match == RuleEntity(domains = "geosite:category-ads-all", outbound = -2) &&
                name in setOf("Block ADs", "屏蔽广告", "封鎖廣告") ->
                app.getString(R.string.route_opt_block_ads)
            match == RuleEntity(domains = PLAY_CHINA_DOMAINS) &&
                name in setOf("Proxy Google Play domains for China", "Google Play 中国域名走代理", "Google Play 中國網域走代理") ->
                app.getString(R.string.route_builtin_play_china)
            match == RuleEntity(ruleset = MAINLAND_CHINA_DOMAINS, outbound = -1) &&
                name in setOf("Mainland China domain bypass", "中国大陆域名直连", "中國大陸網域直連") ->
                app.getString(R.string.route_mainland_china_domains)
            match == RuleEntity(ruleset = CHINA_IP_RULE_SET, outbound = -1) &&
                name in setOf("IP rule for 中国", "中国 IP 规则", "中国 IP 規則", "中國 IP 規則") ->
                app.getString(R.string.route_bypass_ip, app.getString(R.string.route_region_china))
            else -> name.takeIf { it.isNotBlank() } ?: "Rule $id"
        }
    }

    fun mkSummary(): String {
        val match = copy(id = 0L, name = "", userOrder = 0L, enabled = false)
        val description = when (match) {
            RuleEntity(port = "443", network = "udp", outbound = -2) -> R.string.route_builtin_udp_443_summary
            RuleEntity(domains = "geosite:category-ads-all", outbound = -2) -> R.string.route_builtin_ads_summary
            RuleEntity(domains = PLAY_CHINA_DOMAINS) -> R.string.route_builtin_play_china_summary
            else -> null
        }
        if (description != null) return app.getString(description)

        var summary = ""
        if (config.isNotBlank()) summary += "[config]\n"
        if (domains.isNotBlank()) summary += "$domains\n"
        if (ip.isNotBlank()) summary += "$ip\n"
        if (source.isNotBlank()) summary += "src ip: $source\n"
        if (sourcePort.isNotBlank()) summary += "src port: $sourcePort\n"
        if (port.isNotBlank()) summary += "dst port: $port\n"
        if (network.isNotBlank()) summary += "network: $network\n"
        if (protocol.isNotBlank()) summary += "protocol: $protocol\n"
        if (ruleset.isNotBlank()) summary += "$ruleset\n"
        if (packages.isNotEmpty()) summary += app.getString(
            R.string.apps_message, packages.size
        ) + "\n"
        val lines = summary.trim().split("\n")
        return if (lines.size > 3) {
            lines.subList(0, 3).joinToString("\n", postfix = "\n...")
        } else {
            summary.trim()
        }
    }

    fun displayOutbound(): String {
        return when (outbound) {
            0L -> app.getString(R.string.route_proxy)
            -1L -> app.getString(R.string.route_bypass)
            -2L -> app.getString(R.string.route_block)
            else -> ProfileManager.getProfile(outbound)?.displayName()
                ?: app.getString(R.string.error_title)
        }
    }

    companion object {
        private const val MAINLAND_CHINA_DOMAINS =
            "rssite:https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-geolocation-cn.srs"
        private const val CHINA_IP_RULE_SET =
            "rsip:https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs"

        internal const val PLAY_CHINA_DOMAINS =
            "domain:googleapis.cn\ndomain:xn--ngstr-lra8j.com\ndomain:xn--ngstr-cn-8za9o.com"
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * from rules WHERE (packages != '') AND enabled = 1")
        fun checkVpnNeeded(): List<RuleEntity>

        @Query("SELECT * FROM rules ORDER BY userOrder")
        fun allRules(): List<RuleEntity>

        @Query("SELECT * FROM rules WHERE enabled = :enabled ORDER BY userOrder")
        fun enabledRules(enabled: Boolean = true): List<RuleEntity>

        @Query("SELECT MAX(userOrder) + 1 FROM rules")
        fun nextOrder(): Long?

        @Query("SELECT * FROM rules WHERE id = :ruleId")
        fun getById(ruleId: Long): RuleEntity?

        @Query("DELETE FROM rules WHERE id = :ruleId")
        fun deleteById(ruleId: Long): Int

        @Delete
        fun deleteRule(rule: RuleEntity)

        @Delete
        fun deleteRules(rules: List<RuleEntity>)

        @Insert
        fun createRule(rule: RuleEntity): Long

        @Update
        fun updateRule(rule: RuleEntity)

        @Update
        fun updateRules(rules: List<RuleEntity>)

        @Query("DELETE FROM rules")
        fun reset()

        @Insert
        fun insert(rules: List<RuleEntity>)

    }


}
