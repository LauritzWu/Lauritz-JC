package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListListener
import libcore.Libcore
import moe.matsuri.nb4a.utils.SendLog

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutLogcatBinding
    private val logAccess = Mutex()
    private var displayedLog: String? = null
    private var searchQuery = ""
    private val colorCodes = Regex("""(?:\u001B\[|\[(?=[0-9;]+m))[0-9;]*m""")

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)

        if (Build.VERSION.SDK_INT >= 23) {
            binding.textview.breakStrategy = 0 // simple
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        displayedLog = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    reloadSession()
                    delay(1000)
                }
            }
        }
    }

    private fun getColorForLine(line: String): ForegroundColorSpan {
        var color = ForegroundColorSpan(Color.GRAY)
        when {
            line.contains("INFO[") || line.contains(" [Info]") -> {
                color = ForegroundColorSpan((0xFF86C166).toInt())
            }

            line.contains("ERROR[") || line.contains(" [Error]") -> {
                color = ForegroundColorSpan(Color.RED)
            }

            line.contains("WARN[") || line.contains(" [Warning]") -> {
                color = ForegroundColorSpan(Color.RED)
            }
        }
        return color
    }

    private suspend fun reloadSession(force: Boolean = false) = logAccess.withLock {
        val current = binding
        if (!force && current.scroolview.canScrollVertically(1)) return@withLock
        val previous = displayedLog
        val query = searchQuery
        val rendered = withContext(Dispatchers.IO) {
            val raw = readLogTail(current.root.context.cacheDir) ?: return@withContext null
            val clean = colorCodes.replace(raw, "").let { log ->
                if (query.isEmpty()) log
                else log.lineSequence().filter { it.contains(query, ignoreCase = true) }.joinToString("\n")
            }
            if (!force && clean == previous) return@withContext null

            val span = SpannableString(
                if (query.isNotEmpty() && clean.isEmpty()) current.root.context.getString(R.string.logcat_search_empty)
                else clean
            )
            var offset = 0
            for (line in clean.lineSequence()) {
                if (line.isNotEmpty()) {
                    span.setSpan(getColorForLine(line), offset, offset + line.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (query.isNotEmpty()) {
                    var match = line.indexOf(query, ignoreCase = true)
                    while (match >= 0) {
                        val start = offset + match
                        val end = start + query.length
                        span.setSpan(BackgroundColorSpan(0xFFFFDB80.toInt()), start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                        span.setSpan(ForegroundColorSpan(Color.BLACK), start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                        match = line.indexOf(query, startIndex = match + query.length, ignoreCase = true)
                    }
                }
                offset += line.length + 1
            }
            clean to span
        } ?: return@withLock
        if (query != searchQuery || (!force && current.scroolview.canScrollVertically(1))) return@withLock

        displayedLog = rendered.first
        current.textview.text = rendered.second
        current.textview.doOnLayout {
            current.scroolview.scrollTo(0, current.textview.height)
        }
    }

    private fun readLogTail(cacheDir: File): String? {
        val file = File(cacheDir, "neko.log")
        return try {
            RandomAccessFile(file, "r").use { reader ->
                val length = reader.length()
                val start = (length - 50 * 1024L).coerceAtLeast(0)
                reader.seek(start)
                val bytes = ByteArray((length - start).toInt())
                val count = reader.read(bytes).coerceAtLeast(0)
                val tail = String(bytes, 0, count, Charsets.UTF_8)
                if (start > 0) tail.substringAfter('\n', tail) else tail
            }
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun showDomainDiagnostics() {
        val context = requireContext()
        val input = EditText(context).apply {
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setHint(R.string.logcat_domain_hint)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.logcat_domain_diagnostics)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val domain = runCatching {
                java.net.IDN.toASCII(input.text.toString().trim().removeSuffix("."))
            }.getOrDefault("").lowercase()
            if (!domainPattern.matches(domain)) {
                input.error = context.getString(R.string.logcat_domain_invalid)
                return@setOnClickListener
            }
            dialog.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                val events = withContext(Dispatchers.IO) {
                    val raw = readLogTail(context.cacheDir).orEmpty()
                    domainEvents(colorCodes.replace(raw, ""), domain)
                }
                val report = if (events.isEmpty()) {
                    context.getString(R.string.logcat_domain_empty)
                } else {
                    val summary = buildList {
                        events.lastOrNull { it.groupValues[3].startsWith("dns: selected ") }?.let {
                            add(context.getString(R.string.logcat_domain_dns, it.groupValues[3].removePrefix("dns: selected ")))
                        }
                        events.lastOrNull {
                            it.groupValues[3].startsWith("router: match[") && it.groupValues[3].contains("=> route(")
                        }?.let {
                            add(context.getString(R.string.logcat_domain_route, it.groupValues[3].substringAfter("] ")))
                        }
                        events.lastOrNull { it.groupValues[3].startsWith("outbound/") }?.let {
                            val outbound = it.groupValues[3]
                            val name = outbound.substringAfter('[').substringBefore("]: outbound", outbound)
                            add(context.getString(R.string.logcat_domain_outbound, name))
                        }
                    }
                    buildString {
                        if (summary.isNotEmpty()) {
                            appendLine(context.getString(R.string.logcat_domain_summary))
                            appendLine(summary.joinToString("\n"))
                            appendLine()
                        }
                        appendLine(context.getString(R.string.logcat_domain_details))
                        append(formatDiagnosticEvents(events))
                    }
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(domain)
                    .setMessage(report)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showAppDiagnostics() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                val raw = readLogTail(context.cacheDir).orEmpty()
                parseEvents(colorCodes.replace(raw, ""))
            }
            val packages = events.asReversed().mapNotNull { event ->
                appEventPattern.matchEntire(event.groupValues[3])?.groupValues?.get(1)
            }.distinct()
            if (packages.isEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.logcat_app_diagnostics)
                    .setMessage(R.string.logcat_app_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }

            val packageManager = context.packageManager
            val choices = packages.map { packageName ->
                val label = runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                }.getOrNull()?.toString()
                if (label.isNullOrBlank() || label == packageName) packageName else "$label\n$packageName"
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.logcat_app_diagnostics)
                .setItems(choices.toTypedArray()) { _, index ->
                    val packageName = packages[index]
                    val matchingIds = events.mapNotNull { event ->
                        val owner = appEventPattern.matchEntire(event.groupValues[3])?.groupValues?.get(1)
                        event.groupValues[2].takeIf { owner == packageName }
                    }.toSet()
                    val connections = matchingEvents(events, matchingIds) { message ->
                        appConnectionPattern.containsMatchIn(message)
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle(choices[index])
                        .setMessage(formatDiagnosticEvents(connections).ifEmpty {
                            context.getString(R.string.logcat_app_no_connections)
                        })
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun parseEvents(log: String): List<MatchResult> =
        log.lineSequence().mapNotNull { eventPattern.matchEntire(it) }.toList()

    private fun formatDiagnosticEvents(events: List<MatchResult>): String =
        events.takeLast(80).joinToString("\n\n") { event ->
            "[%s] [%s] %s".format(event.groupValues[1], event.groupValues[2], event.groupValues[3])
        }

    private fun domainEvents(log: String, domain: String): List<MatchResult> {
        val events = parseEvents(log)
        val matchingIds = events.mapNotNull { event ->
            val host = domainEventPattern.find(event.groupValues[3])
                ?.groupValues?.get(1)?.trimEnd('.')?.lowercase()
            event.groupValues[2].takeIf { host == domain || host?.endsWith(".$domain") == true }
        }.toSet()
        return matchingEvents(events, matchingIds) { diagnosticEventPattern.containsMatchIn(it) }
    }

    private fun matchingEvents(
        events: List<MatchResult>,
        matchingIds: Set<String>,
        include: (String) -> Boolean,
    ): List<MatchResult> {
        val seenOutbound = mutableSetOf<Pair<String, String>>()
        return events.asReversed().asSequence()
            .filter { it.groupValues[2] in matchingIds }
            .filter { include(it.groupValues[3]) }
            .filter { event ->
                val message = event.groupValues[3]
                !message.startsWith("outbound/") || seenOutbound.add(event.groupValues[2] to message)
            }
            .toList()
            .asReversed()
    }

    private companion object {
        val domainPattern = Regex("""(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?""")
        val eventPattern = Regex("""^[A-Z]+\[(\d+)] \[(\d+)(?: [^\]]+)?] (.+)$""")
        val domainEventPattern = Regex("""(?:^dns: (?:exchange|exchanged|selected)(?: CNAME| AAAA| A| SOA)? |^router: .*?\bdomain: )([a-zA-Z0-9._-]+)(?=[\s,]|$)""")
        val appEventPattern = Regex("""^router: found package name: ([a-zA-Z0-9_.]+)$""")
        val appConnectionPattern = Regex("""^(?:inbound/.*?\b(?:packet )?connection to |router: .*?(?:\bdomain: |=> route\()|outbound/)""")
        val diagnosticEventPattern = Regex("""^(?:dns: |router: .*?(?:\bdomain: |=> route\()|outbound/)""")
    }

    private fun showSearch() {
        val input = EditText(requireContext()).apply {
            setSingleLine()
            setHint(R.string.logcat_search_hint)
            setText(searchQuery)
            selectAll()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logcat_search)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                searchQuery = input.text.toString().trim()
                viewLifecycleOwner.lifecycleScope.launch { reloadSession(force = true) }
            }
            .setNeutralButton(R.string.logcat_search_clear) { _, _ ->
                searchQuery = ""
                viewLifecycleOwner.lifecycleScope.launch { reloadSession(force = true) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search_logcat -> showSearch()
            R.id.action_domain_diagnostics -> showDomainDiagnostics()
            R.id.action_app_diagnostics -> showAppDiagnostics()

            R.id.action_clear_logcat -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    logAccess.withLock {
                        try {
                            withContext(Dispatchers.IO) {
                                Libcore.nekoLogClear()
                                Runtime.getRuntime().exec("/system/bin/logcat -c")
                            }
                            displayedLog = null
                            binding.textview.text = ""
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }

            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "NB4A")
                }
            }

            R.id.action_logcat_latest -> {
                viewLifecycleOwner.lifecycleScope.launch { reloadSession(force = true) }
            }
        }
        return true
    }

}