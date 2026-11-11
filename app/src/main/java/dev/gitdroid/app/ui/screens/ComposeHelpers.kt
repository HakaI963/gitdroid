package dev.gitdroid.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Collects a [StateFlow] into Compose state. */
@Composable
fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = collectAsState()

/** A scope tied to the composition. */
@Composable
fun rememberCoroutineScopeCompat(): CoroutineScope = rememberCoroutineScope()
