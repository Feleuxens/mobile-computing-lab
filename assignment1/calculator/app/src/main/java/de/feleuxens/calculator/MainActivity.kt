package de.feleuxens.calculator

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.startActivityForResult
import de.feleuxens.calculator.ui.theme.CalculatorTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculatorUI()
                }
            }
        }
    }
}

fun historyToString(history: List<Pair<String, String>>): String {
    val result: StringBuilder = StringBuilder()
    for (pair in history) {
        result.append(pair.first)
        result.append(" = ")
        result.append(pair.second)
        result.append("\n")
    }
    return result.toString()
}

fun saveHistory(context: Context, history: List<Pair<String, String>>) {
    val resolver = context.contentResolver

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "history.txt")
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)

    uri?.let {
        resolver.openOutputStream(it)?.use { outputStream ->
            outputStream.write(historyToString(history).toByteArray())
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        Toast.makeText(context, "History saved successfully!", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CalculatorUI() {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Pair<String, String>>() }

    val context = LocalContext.current

    Column {
        TextField(expression, result)
        Spacer(Modifier.height(8.dp))
        Controls(
            expression = expression,
            onExpressionChange = { expression = it },
            onResultChange = { result = it },
            onEvaluate = {
                try {
                    result = evaluate(expression).toString()
                    history.add(Pair(expression, result))
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            },
            onExport = { saveHistory(context, history) }
        )
        for (i in history.indices.reversed()) {
            if(i <= history.size-5) break
            TextField(history[i].first, history[i].second)
        }
    }
}

@Composable
fun TextField(expression: String, result: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(
            text = expression,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = result,
            fontSize = 24.sp,
            color = Color.Gray,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Controls(
    expression: String,
    onExpressionChange: (String) -> Unit,
    onResultChange: (String) -> Unit,
    onEvaluate: () -> Unit,
    onExport: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button("", "complex") {}
        Button("(", "complex") { onExpressionChange("$expression(") }
        Button(")", "complex") { onExpressionChange("$expression)") }
        Button("Export", "complex", 2) { onExport() }
    }
    Row {
        Button("7", "num") { onExpressionChange(expression + "7") }
        Button("8", "num") { onExpressionChange(expression + "8") }
        Button("9", "num") { onExpressionChange(expression + "9") }
        Button("/", "basic") { onExpressionChange("$expression/") }
        Button("CE", "basic") {
            onExpressionChange("")
            onResultChange("")
        }
    }
    Row {
        Button("4", "num") { onExpressionChange(expression + "4") }
        Button("5", "num") { onExpressionChange(expression + "5") }
        Button("6", "num") { onExpressionChange(expression + "6") }
        Button("*", "basic") { onExpressionChange("$expression*") }
        Button("", "basic") {  }
    }
    Row {
        Button("1", "num") { onExpressionChange(expression + "1") }
        Button("2", "num") { onExpressionChange(expression + "2") }
        Button("3", "num") { onExpressionChange(expression + "3") }
        Button("-", "basic") { onExpressionChange("$expression-") }
        Button("", "basic") {  }
    }
    Row {
        Button(",", "num") { onExpressionChange("$expression,") }
        Button("0", "num") { onExpressionChange(expression + "0") }
        Button("", "num") {  }
        Button("+", "basic") { onExpressionChange("$expression+") }
        Button("=", "basic") { onEvaluate() }
    }
}

@Composable
fun Button(label: String, type: String, onClick: () -> Unit) {
    val color: Color = when (type) {
        "complex" -> Color.Cyan
        "num" -> Color.DarkGray
        else -> {
            Color.LightGray
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(80.dp).background(color).clickable { onClick() }
    ) {
        Text(text = label, fontSize = 24.sp)
    }
}

@Composable
fun Button(label: String, type: String, width: Int, onClick: () -> Unit) {
    val color: Color = when (type) {
        "complex" -> Color.Cyan
        "num" -> Color.DarkGray
        else -> {
            Color.LightGray
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(80.dp * width, 80.dp).background(color).clickable { onClick() }
    ) {
        Text(text = label, fontSize = 24.sp)
    }
}


@Preview(showBackground = true)
@Composable
fun CalculatorPreview() {
    CalculatorTheme {
        CalculatorUI()
    }
}