package com.example.scoreturner

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val repo = remember { WorksRepository.get(ctx) }
    val settingsRepo = remember { SettingsRepository(ctx.applicationContext) }
    val settings by settingsRepo.settingsFlow.collectAsState(initial = Settings())

    var pendingImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                openSettings = { nav.navigate("settings") },
                openReader = { id -> nav.navigate("reader/$id") },
                openNewImagesFlow = { uris -> pendingImages = uris; nav.navigate("newImages") },
                repo = repo
            )
        }
        composable("newImages") {
            NewImagesScreen(
                initialUris = pendingImages,
                repo = repo,
                onCancel = { nav.popBackStack() },
                onSavedOpenReader = { id -> nav.navigate("reader/$id") { popUpTo("home") } }
            )
        }
        composable(
            route = "reader/{workId}",
            arguments = listOf(navArgument("workId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workId = backStackEntry.arguments!!.getLong("workId")
            ReaderScreen(
                workId = workId,
                settings = settings,
                repo = repo,
                openSettings = { nav.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(settings = settings, repo = settingsRepo, onBack = { nav.popBackStack() })
        }
    }
}
