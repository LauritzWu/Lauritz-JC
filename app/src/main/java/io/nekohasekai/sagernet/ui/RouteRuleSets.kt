package io.nekohasekai.sagernet.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class RouteRuleSets(private val context: Context, private val owner: LifecycleOwner) {
    private val preferences = context.getSharedPreferences("route_rule_sets", Context.MODE_PRIVATE)

    private fun entries(): Map<String, String> {
        val stored = JSONObject(preferences.getString("sources", "{}") ?: "{}")
        return stored.keys().asSequence().associateWith { stored.getString(it) }.toSortedMap()
    }

    private fun save(entries: Map<String, String>) {
        preferences.edit().putString("sources", JSONObject(entries).toString()).apply()
    }

    fun manage() {
        val sources = entries().entries.toList()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.route_rule_sets)
            .setPositiveButton(R.string.route_rule_set_add) { _, _ -> add() }
            .setNegativeButton(android.R.string.cancel, null)
        if (sources.isEmpty()) {
            dialog.setMessage(R.string.route_rule_set_empty)
        } else {
            dialog.setItems(sources.map { it.key }.toTypedArray()) { _, index ->
                val (name, url) = sources[index]
                details(name, url)
            }
        }
        dialog.show()
    }

    fun choose(onSelected: (String) -> Unit) {
        val sources = entries().entries.toList()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.route_rule_set_choose)
            .setNegativeButton(android.R.string.cancel, null)
        if (sources.isEmpty()) {
            dialog.setMessage(R.string.route_rule_set_empty)
                .setPositiveButton(R.string.route_rule_set_add) { _, _ -> add(onSelected) }
        } else {
            dialog.setItems(sources.map { it.key }.toTypedArray()) { _, index ->
                onSelected(sources[index].value)
            }
        }
        dialog.show()
    }

    private fun add(onSelected: ((String) -> Unit)? = null) {
        val name = EditText(context).apply {
            hint = context.getString(R.string.route_rule_set_name)
            isSingleLine = true
        }
        val source = EditText(context).apply {
            hint = "https://…/rules.srs"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(24), 0, dp2px(24), 0)
            addView(name)
            addView(source)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.route_rule_set_add)
            .setView(fields)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val label = name.text.toString().trim()
            val url = source.text.toString().trim().toHttpUrlOrNull()
            val sources = entries()
            when {
                label.isEmpty() -> name.error = context.getString(R.string.route_rule_set_name)
                label in sources -> name.error = context.getString(R.string.route_rule_set_duplicate)
                url == null || !url.isHttps || !url.encodedPath.endsWith(".srs") ||
                    url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null || ',' in url.toString() ->
                    source.error = context.getString(R.string.route_rule_set_invalid_url)
                else -> {
                    save(sources + (label to url.toString()))
                    dialog.dismiss()
                    if (onSelected == null) manage() else onSelected(url.toString())
                }
            }
        }
    }

    private fun details(name: String, url: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(name)
            .setMessage(url)
            .setPositiveButton(R.string.route_rule_set_check) { _, _ -> checkSource(name, url) }
            .setNeutralButton(R.string.route_rule_set_remove) { _, _ ->
                save(entries() - name)
                manage()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun checkSource(name: String, url: String) {
        owner.lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful && response.body?.source()?.request(1) == true) {
                            R.string.route_rule_set_reachable
                        } else {
                            R.string.route_rule_set_unreachable
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    R.string.route_rule_set_unreachable
                }
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(name)
                .setMessage(status)
                .setPositiveButton(android.R.string.ok) { _, _ -> details(name, url) }
                .show()
        }
    }

    private companion object {
        val client = OkHttpClient.Builder()
            .followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
