package de.team10.task2

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.ComponentActivity.BIND_AUTO_CREATE
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BaseLayout(title = stringResource(R.string.app_name)) {
                MainContent(Modifier.padding(10.dp), application)
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier, application: Application) {
    val context = LocalContext.current
    var intervalText by remember { mutableStateOf("1000") }
    var thresholdText by remember { mutableStateOf("10000") }

    var sensorValue by remember { mutableStateOf(0.0f) }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val value = intent?.getFloatExtra("value", 0.0f)
            if (value != null) {
                sensorValue = value
            }
        }
    }

    LocalBroadcastManager.getInstance(application).registerReceiver(receiver, IntentFilter("de.team10.task2.timer.UPDATE"))

    Column {
        TextField(
            value = intervalText,
            onValueChange = { intervalText = it },
            label = { Text("Interval in ms") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        TextField(
            value = thresholdText,
            onValueChange = { thresholdText = it },
            label = { Text("Threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Button(
            onClick = {
                val interval = intervalText.toLongOrNull() ?: 1000
                val threshold = thresholdText.toLongOrNull() ?: 10000
                val intent = Intent(context, SensorReader::class.java).apply {
                    action = "START_TIMER"
                    putExtra("interval", interval)
                    putExtra("threshold", threshold)
                }
                context.startService(intent)
            }
        ) {
            Text("Start Service")
        }


        Button(
            onClick = {
                val interval = intervalText.toLongOrNull() ?: 1000
                val threshold = thresholdText.toLongOrNull() ?: 10000
                val intent = Intent(context, SensorReader::class.java).apply {
                    action = "UPDATE_PARAMS"
                    putExtra("interval", interval)
                    putExtra("threshold", threshold)
                }
                context.startService(intent)
            }
        ) {
            Text("Update")
        }

        Text(
            text = "${sensorValue}"
        )


    }

}