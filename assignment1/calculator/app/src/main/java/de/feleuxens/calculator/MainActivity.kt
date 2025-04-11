package de.feleuxens.calculator

import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.feleuxens.calculator.ui.theme.CalculatorTheme

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

@Composable
fun CalculatorUI() {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Column {
        TextField( expression, result)
        Spacer(Modifier.height(8.dp))
        Controls(
            expression = expression,
            onExpressionChange = { expression = it },
            onResultChange = { result = it },
            onEvaluate = { result = evaluate(expression).toString() }
        )
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
    onEvaluate: () -> Unit
) {
    Row {
        Button("", "complex") {}
        Button("", "complex") {}
        Button("(", "complex") { onExpressionChange("$expression(") }
        Button(")", "complex") { onExpressionChange("$expression)") }
        Button("", "complex") {}
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
        Button("*", "basic") { onExpressionChange(expression + "*") }
        Button("", "basic") {  }
    }
    Row {
        Button("1", "num") { onExpressionChange(expression + "1") }
        Button("2", "num") { onExpressionChange(expression + "2") }
        Button("3", "num") { onExpressionChange(expression + "3") }
        Button("-", "basic") { onExpressionChange(expression + "-") }
        Button("", "basic") {  }
    }
    Row {
        Button(",", "num") { onExpressionChange(expression + ",") }
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


@Preview(showBackground = true)
@Composable
fun CalculatorPreview() {
    CalculatorTheme {
        CalculatorUI()
    }
}