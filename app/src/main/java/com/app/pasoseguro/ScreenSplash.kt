package com.app.pasoseguro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class ScreenSplash : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screensplash_layout)

        CoroutineScope(Dispatchers.Main).launch {
            delay(800)
            val intent = Intent(this@ScreenSplash, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}