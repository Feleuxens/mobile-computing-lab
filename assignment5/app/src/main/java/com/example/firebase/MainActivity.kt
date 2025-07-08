package com.example.firebase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.firebase.ui.theme.FirebaseTheme

data class CityTemperature(val city: String, val temperature: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseTheme {
                TemperatureData()
            }
        }
    }

    @Composable
    fun TemperatureData() {
        var cityInput by remember { mutableStateOf("") }
        var temperatureInput by remember { mutableStateOf("") }
        val cityList = remember { mutableStateListOf<CityTemperature>() }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {

            // City Input
            OutlinedTextField(
                value = cityInput,
                onValueChange = { cityInput = it },
                label = { Text("City Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Temperature Input
            OutlinedTextField(
                value = temperatureInput,
                onValueChange = { temperatureInput = it },
                label = {Text("Temperature (°C)")},
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Add Button
            Button(
                onClick = {
                    val temp = temperatureInput.toDoubleOrNull()
                    if (cityInput.isNotBlank() && temp != null) {
                        cityList.add(CityTemperature(cityInput.trim(), temp))
                        cityInput = ""
                        temperatureInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add City")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display List of Cities & Temperatures
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(cityList) { cityTemp ->
                    Text(
                        text = "${cityTemp.city}: ${cityTemp.temperature}°C",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        FirebaseTheme {
            Greeting("Android")
        }
    }
}