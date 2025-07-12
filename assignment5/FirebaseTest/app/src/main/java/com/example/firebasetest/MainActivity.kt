package com.example.firebasetest

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.reflect.typeOf

data class CityTemperature(val timestamp: Long, val temperature: Double)

class MainActivity : ComponentActivity() {
    var database: DatabaseReference = FirebaseDatabase.getInstance().getReference()
    var testRoot = database.child("teams").child("10")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseTestTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    TemperatureData(testRoot)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
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
                if(child.key == null) continue // no city name
                val cityName = child.key

               // if(cityName == "Stuttgart ") {

               //     child.ref.removeValue()
               //     continue
               // }

                var averageSum = 0.0
                var averageCount = 0
                val today = getCurrentDate()

                var latestTime = 0L
                var latestTemp: Double? = null

                for(day in child.children) {
                    if(day.key == null) continue // no date provided
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    try {
                        LocalDate.parse(day.key, formatter)
                    } catch (e: DateTimeParseException) {
                        Log.i("firebaseEvent", day.key.toString() + " is not a date... skipping")


                        continue // not a valid date
                    }

                    val calculateAverage = day.key == today

                    if(calculateAverage) {
                        averageTemperature = 0.0 // reset Temperature
                    }


                    for(entry in day.children) {
                        if(entry == null) continue // no entry
                        if(entry.key == null) continue // no timestamp provided
                        if(entry.value == null) continue // no temperature provided
                        // if(entry.value !is Double ) continue // no Double for the temperature

                        val millis = entry.key!!.toLongOrNull()
                        val temp = entry.value.toString().toDouble()

                        if(millis != null && millis > latestTime) {
                            latestTime = millis
                            latestTemp = temp
                        }

                        if(calculateAverage) {
                            averageSum += temp
                            averageCount++
                        }
                    }
                }

                if(averageCount == 0) {
                    averageTemperature = 0.0
                } else {
                    averageTemperature = averageSum / averageCount
                }

                if(latestTime == 0L || latestTemp == null) continue // no temperature found for this city

                cityList[cityName!!] = CityTemperature(latestTime, latestTemp)

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
                    val formattedDate = getCurrentDate()
                    root.child(cityInput).child(formattedDate).child(System.currentTimeMillis().toString()).setValue(temp as Double)
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

@RequiresApi(Build.VERSION_CODES.O)
fun getCurrentDate(): String {
    val currentDate = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val formattedDate = currentDate.format(formatter)
    return formattedDate
}

fun printSubtree(snapshot: DataSnapshot, indent: String = "") {
    println("$indent${snapshot.key}: ${snapshot.value}")

    for (child in snapshot.children) {
        printSubtree(child, indent + "  ")
    }
}
