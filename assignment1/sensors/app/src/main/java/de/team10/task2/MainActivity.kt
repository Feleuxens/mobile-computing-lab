package de.team10.task2

import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

    Row {
        SensorView(
            application,
            LightSensorReader::class.java,
            0f,
            1000,
            10000,
            "de.team10.task2.timer.light.UPDATE",
                "de.team10.task2.timer.light.THRESHOLD"
        )


        SensorView(
            application,
            ProximitySensorReader::class.java,
            10f,
            1000,
            5,
            "de.team10.task2.timer.proximity.UPDATE",
            "de.team10.task2.timer.proximity.THRESHOLD"
        )
    }
}

@Composable
fun <T: Service> SensorView(application: Application, cls: Class<T>, defaultValue: Float, intervalDefault: Long, thresholdDefault: Long, updateTopic: String, thresholdTopic: String) {
    val context = LocalContext.current
    var intervalText by remember { mutableStateOf(intervalDefault.toString()) }
    var thresholdText by remember { mutableStateOf(thresholdDefault.toString()) }

    var sensorValue by remember { mutableFloatStateOf(0.0f) }

    val receiverUpdate = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val value = intent?.getFloatExtra("value", 0.0f)
            if (value != null) {
                sensorValue = value
            }
        }
    }

    val receiverThreshold = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val value = intent?.getFloatExtra("value", 0.0f)
            if (value != null) {
                Toast.makeText(application, value.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }



    LocalBroadcastManager.getInstance(application).registerReceiver(receiverUpdate, IntentFilter(updateTopic))
    LocalBroadcastManager.getInstance(application).registerReceiver(receiverThreshold, IntentFilter(thresholdTopic))

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
                val interval = intervalText.toLongOrNull() ?: intervalDefault
                val threshold = thresholdText.toLongOrNull() ?: thresholdDefault
                val intent = Intent(context, cls).apply {
                    action = "START_TIMER"
                    putExtra("interval", interval)
                    putExtra("threshold", threshold)
                }
                ContextCompat.startForegroundService(application, intent)
                // context.startService(intent)
            }
        ) {
            Text("Start Service")
        }


        Button(
            onClick = {
                val interval = intervalText.toLongOrNull() ?: intervalDefault
                val threshold = thresholdText.toLongOrNull() ?: thresholdDefault
                val intent = Intent(context, cls).apply {
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