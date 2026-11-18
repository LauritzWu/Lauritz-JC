package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
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
        val rendered = withContext(Dispatchers.IO) {
            val file = File(current.root.context.cacheDir, "neko.log")
            val raw = try {
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
                return@withContext null
            }
            val clean = colorCodes.replace(raw, "")
            if (!force && clean == previous) return@withContext null

            val span = SpannableString(clean)
            var offset = 0
            for (line in clean.lineSequence()) {
                if (line.isNotEmpty()) {
                    span.setSpan(getColorForLine(line), offset, offset + line.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                offset += line.length + 1
            }
            clean to span
        } ?: return@withLock
        if (!force && current.scroolview.canScrollVertically(1)) return@withLock

        displayedLog = rendered.first
        current.textview.text = rendered.second
        current.textview.doOnLayout {
            current.scroolview.scrollTo(0, current.textview.height)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
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