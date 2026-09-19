package com.example.sportpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.sportpro.ui.prototypes.*
import com.example.sportpro.ui.theme.SportProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SportProAppHost()
                }
            }
        }
    }
}

@Composable
fun SportProAppHost() {
    var currentScreenCode by remember { mutableStateOf("MENU") }

    val navigateToMenu = { currentScreenCode = "MENU" }

    when (currentScreenCode) {
        "MENU" -> PrototypeMenuScreen(onSelectPrototype = { code -> currentScreenCode = code })
        "US-01" -> US01LiveMatchScreen(onBack = navigateToMenu)
        "US-02" -> US02AiSummaryScreen(onBack = navigateToMenu)
        "US-03" -> US03PlayerProfileScreen(onBack = navigateToMenu)
        "US-04" -> US04CallUpScreen(onBack = navigateToMenu)
        "US-05" -> US05TrainingLibraryScreen(onBack = navigateToMenu)
        "US-06" -> US06CommunityScreen(onBack = navigateToMenu)
        "US-07" -> US07AnnouncementsScreen(onBack = navigateToMenu)
        "US-08" -> US08TeamsCategoriesScreen(onBack = navigateToMenu)
        "US-09" -> US09LineupBuilderScreen(onBack = navigateToMenu)
        "US-10" -> US10MonthlyPaymentsScreen(onBack = navigateToMenu)
        "US-11" -> US11PlayerStatsScreen(onBack = navigateToMenu)
        "US-12" -> US12TryoutsScreen(onBack = navigateToMenu)
        else -> PrototypeMenuScreen(onSelectPrototype = { code -> currentScreenCode = code })
    }
}
