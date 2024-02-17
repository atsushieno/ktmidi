package dev.atsushieno.ktmidi.citool

import AndroidPlatform
import android.os.Bundle
import android.widget.Toast
import androidPlatform
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.atsushieno.ktmidi.AndroidMidi2Access
import dev.atsushieno.ktmidi.citool.view.App
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidPlatform = AndroidPlatform(applicationContext)
        initializeAppModel(this)
        AppModel.midiDeviceManager.midiAccess = AndroidMidi2Access(applicationContext, true)

        setContent {
            App()
        }

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            private var lastBackPressed = System.currentTimeMillis()
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - lastBackPressed < 2000) {
                    finish()
                    exitProcess(0)
                }
                else
                    Toast.makeText(this@MainActivity, "Tap once more to quit", Toast.LENGTH_SHORT).show()
                lastBackPressed = System.currentTimeMillis()
            }
        })
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}