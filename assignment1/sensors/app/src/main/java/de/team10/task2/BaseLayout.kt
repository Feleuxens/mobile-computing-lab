package de.team10.task2

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.team10.task2.ui.theme.Task2Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseLayout(
    title: String,
    content: @Composable (
        innerPadding: PaddingValues,
    ) -> Unit
) {
    Task2Theme  {
        Scaffold(
           // topBar = {
           //     TopAppBar(
           //         colors = TopAppBarDefaults.topAppBarColors(
           //             containerColor = MaterialTheme.colorScheme.primary,
           //             titleContentColor = MaterialTheme.colorScheme.onPrimary
           //         ),
           //         title = { Text(title) }
           //     )
           // },
            modifier = Modifier.fillMaxSize()) { innerPadding ->
            content(innerPadding)
        }
    }
}