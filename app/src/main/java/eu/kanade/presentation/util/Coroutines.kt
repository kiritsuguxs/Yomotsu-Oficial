package eu.kanade.presentation.util

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

val ScreenModel.ioCoroutineScope: CoroutineScope
    get() = screenModelScope + Dispatchers.IO
