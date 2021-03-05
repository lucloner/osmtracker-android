package net.vicp.biggee.android.OSMTracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import net.osmtracker.activity.TrackManager
import net.vicp.biggee.android.osmtracker.R
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        b_textView.append("\n\n\t" + InputStreamReader(resources.assets.open("text.txt")).readText().replace("\n", "\n\t"))

        b_button.setOnClickListener {
            startActivity(Intent(this@MainActivity, TrackManager::class.java))
        }
    }
}