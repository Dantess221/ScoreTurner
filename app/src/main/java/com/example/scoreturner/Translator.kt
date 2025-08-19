package com.example.scoreturner

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import kotlinx.coroutines.tasks.await

@Composable
fun t(original: String): String {
    val context = LocalContext.current
    var translated by remember(original) { mutableStateOf<String?>(null) }

    LaunchedEffect(original) {
        translated = translateIfNeeded(context, original)
    }

    return translated ?: original
}

private suspend fun translateIfNeeded(context: Context, text: String): String? {
    val locale = context.resources.configuration.locales[0]
    val target = TranslateLanguage.fromLanguageTag(locale.language) ?: return null
    if (target == TranslateLanguage.RUSSIAN) return null
    val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.RUSSIAN)
        .setTargetLanguage(target)
        .build()
    val translator = Translation.getClient(options)
    return try {
        translator.downloadModelIfNeeded().await()
        translator.translate(text).await()
    } catch (e: Exception) {
        null
    } finally {
        translator.close()
    }
}
