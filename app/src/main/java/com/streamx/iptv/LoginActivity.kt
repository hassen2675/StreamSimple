package com.streamx.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etServer = findViewById<EditText>(R.id.etServer)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val tvError = findViewById<TextView>(R.id.tvError)

        // Load saved login
        val prefs = getSharedPreferences("streamx", MODE_PRIVATE)
        etServer.setText(prefs.getString("server", ""))
        etUser.setText(prefs.getString("user", ""))
        etPass.setText(prefs.getString("pass", ""))

        btnConnect.setOnClickListener {
            val server = etServer.text.toString().trim().trimEnd('/')
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                tvError.text = "Bitte alle Felder ausfüllen"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = View.GONE
            btnConnect.isEnabled = false
            btnConnect.text = "Verbinde..."

            val url = "$server/player_api.php?username=$user&password=$pass"
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        tvError.text = "Server nicht erreichbar"
                        tvError.visibility = View.VISIBLE
                        btnConnect.isEnabled = true
                        btnConnect.text = "VERBINDEN"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    runOnUiThread {
                        try {
                            val json = JSONObject(body)
                            val userInfo = json.optJSONObject("user_info")
                            if (userInfo != null) {
                                // Save login
                                prefs.edit()
                                    .putString("server", server)
                                    .putString("user", user)
                                    .putString("pass", pass)
                                    .apply()

                                // Go to main
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.putExtra("server", server)
                                intent.putExtra("user", user)
                                intent.putExtra("pass", pass)
                                intent.putExtra("username", userInfo.optString("username", user))
                                intent.putExtra("exp", userInfo.optString("exp_date", ""))
                                startActivity(intent)
                                finish()
                            } else {
                                tvError.text = "Ungültige Anmeldedaten"
                                tvError.visibility = View.VISIBLE
                                btnConnect.isEnabled = true
                                btnConnect.text = "VERBINDEN"
                            }
                        } catch (e: Exception) {
                            tvError.text = "Verbindungsfehler: ${e.message}"
                            tvError.visibility = View.VISIBLE
                            btnConnect.isEnabled = true
                            btnConnect.text = "VERBINDEN"
                        }
                    }
                }
            })
        }
    }
}
