package com.example.firebasetest

import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.firebasetest.ui.theme.FirebaseTestTheme
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class CityTemperature(val timestamp: Long, val temperature: Double)

class MainActivity : ComponentActivity() {
    var database: DatabaseReference = FirebaseDatabase.getInstance().getReference()
    var testRoot = database.child("teams").child("10")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseTestTheme {
                TemperatureData(testRoot)
            }
        }
    }
}

@Composable
fun TemperatureData(root: DatabaseReference) {
    var cityInput by remember { mutableStateOf("") }
    var temperatureInput by remember { mutableStateOf("") }
    val cityList = remember { mutableStateMapOf<String, CityTemperature>() }
    var selectedCity by remember { mutableStateOf("") }
    var averageTemperature by remember { mutableStateOf(0.0) }

    val tempListener = object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            cityList.clear()
            for(child in dataSnapshot.children) {
                if(child.key == null) continue
                var selected = false
                if(child.key.equals(selectedCity)) {
                    selected = true
                    averageTemperature = 0.0
                }
                // add error handling
                val values = child.value as Map<String, Double>
                var latest: Long = 0
                values.forEach {entry ->
                    if(selected) {
                        averageTemperature += entry.value * (1.0/values.size)
                    }
                    val millis = entry.key.toLongOrNull()
                    if(millis != null && millis > latest) latest = millis
                }
                // hacky solution but works :D
                val temp = values[latest.toString()].toString().toDouble()
                cityList[child.key as String] = CityTemperature(latest as Long, temp)
            }
        }

        override fun onCancelled(databaseError: DatabaseError) {
            Log.w("firebase", "loadUpdate:onCancelled", databaseError.toException())
        }
    }
    root.addValueEventListener(tempListener)

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
                    root.child(cityInput).child(System.currentTimeMillis().toString()).setValue(temp as Double)
                    cityInput = ""
                    temperatureInput = ""

                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add City")
        }

        Spacer(modifier = Modifier.height(16.dp))
        if(selectedCity in cityList.keys) {
            Text(
                text = "$selectedCity: ${cityList[selectedCity]?.temperature}°C Daily average: ${"%.1f".format(averageTemperature)}°C",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display List of Cities & Temperatures
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(cityList.entries.toList()) { entry ->
                OutlinedButton({selectedCity = entry.key}) {
                    Text(
                        text = "${entry.key}: [${entry.value.timestamp}] ${entry.value.temperature}°C",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}