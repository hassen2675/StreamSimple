package com.streamx.iptv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

data class Channel(val id: Int, val name: String, val url: String, val catId: String)
data class VodItem(val id: Int, val name: String, val ext: String)

class MainActivity : AppCompatActivity() {

    private var server = ""
    private var user = ""
    private var pass = ""
    private var allChannels = mutableListOf<Channel>()
    private var filteredChannels = mutableListOf<Channel>()
    private lateinit var adapter: ChannelAdapter
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        server = intent.getStringExtra("server") ?: ""
        user = intent.getStringExtra("user") ?: ""
        pass = intent.getStringExtra("pass") ?: ""

        val flContent = findViewById<FrameLayout>(R.id.flContent)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val etSearch = findViewById<EditText>(R.id.etSearch)

        // Setup RecyclerView
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        adapter = ChannelAdapter(filteredChannels) { ch ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("url", ch.url)
            intent.putExtra("name", ch.name)
            startActivity(intent)
        }
        rv.adapter = adapter
        flContent.addView(rv)

        // Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterChannels(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Tabs
        findViewById<LinearLayout>(R.id.tabLive).setOnClickListener {
            currentTab = 0
            tvTitle.text = "Live TV"
            loadLive()
        }
        findViewById<LinearLayout>(R.id.tabMovies).setOnClickListener {
            currentTab = 1
            tvTitle.text = "Movies"
            loadVod()
        }
        findViewById<LinearLayout>(R.id.tabSeries).setOnClickListener {
            currentTab = 2
            tvTitle.text = "Serien"
            Toast.makeText(this, "Serien werden geladen...", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.tabSettings).setOnClickListener {
            currentTab = 3
            tvTitle.text = "Info"
            showInfo()
        }

        // Load live channels
        loadLive()
    }

    private fun loadLive() {
        val url = "$server/player_api.php?username=$user&password=$pass&action=get_live_streams"
        fetchJson(url) { json ->
            allChannels.clear()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val id = obj.optInt("stream_id")
                val name = obj.optString("name")
                val catId = obj.optString("category_id", "")
                val streamUrl = "$server/live/$user/$pass/$id.m3u8"
                allChannels.add(Channel(id, name, streamUrl, catId))
            }
            runOnUiThread {
                filteredChannels.clear()
                filteredChannels.addAll(allChannels)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "${allChannels.size} Kanäle geladen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadVod() {
        val url = "$server/player_api.php?username=$user&password=$pass&action=get_vod_streams"
        fetchJson(url) { json ->
            allChannels.clear()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val id = obj.optInt("stream_id")
                val name = obj.optString("name")
                val ext = obj.optString("container_extension", "mp4")
                val catId = obj.optString("category_id", "")
                val streamUrl = "$server/movie/$user/$pass/$id.$ext"
                allChannels.add(Channel(id, name, streamUrl, catId))
            }
            runOnUiThread {
                filteredChannels.clear()
                filteredChannels.addAll(allChannels)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showInfo() {
        val username = intent.getStringExtra("username") ?: user
        val exp = intent.getStringExtra("exp") ?: "—"
        val expDate = if (exp.isNotEmpty() && exp != "null") {
            try { java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date(exp.toLong() * 1000)) }
            catch (e: Exception) { exp }
        } else "∞"

        Toast.makeText(this, "User: $username\nAblauf: $expDate", Toast.LENGTH_LONG).show()
    }

    private fun filterChannels(query: String) {
        filteredChannels.clear()
        if (query.isEmpty()) {
            filteredChannels.addAll(allChannels)
        } else {
            filteredChannels.addAll(allChannels.filter { it.name.contains(query, ignoreCase = true) })
        }
        adapter.notifyDataSetChanged()
    }

    private fun fetchJson(url: String, callback: (JSONArray) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: "[]"
                    val json = JSONArray(body)
                    callback(json)
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Parse Fehler", Toast.LENGTH_SHORT).show() }
                }
            }
        })
    }
}

class ChannelAdapter(
    private val items: List<Channel>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNum: TextView = view.findViewById(R.id.tvNum)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvCat: TextView = view.findViewById(R.id.tvCat)
        val tvHD: TextView = view.findViewById(R.id.tvHD)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvNum.text = "${position + 1}"
        holder.tvName.text = item.name
        holder.tvCat.text = "Stream #${item.id}"
        holder.tvHD.visibility = if (item.name.contains("HD", ignoreCase = true)) View.VISIBLE else View.GONE
        if (item.name.contains("FHD", ignoreCase = true)) {
            holder.tvHD.text = "FHD"
            holder.tvHD.setTextColor(0xFFE8FF47.toInt())
        } else {
            holder.tvHD.text = "HD"
            holder.tvHD.setTextColor(0xFF4D9FFF.toInt())
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
