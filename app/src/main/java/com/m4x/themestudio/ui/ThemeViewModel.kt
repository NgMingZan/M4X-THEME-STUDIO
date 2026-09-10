package com.m4x.themestudio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m4x.themestudio.core.MtzProcessor
import com.m4x.themestudio.data.ThemeInfo
import com.m4x.themestudio.data.ThemeStore
import com.m4x.themestudio.data.TranslationOptions
import com.m4x.themestudio.data.TranslationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ThemeStore(app)
    private val processor = MtzProcessor(app, store)

    private val _themes = MutableStateFlow(store.loadAll())
    val themes: StateFlow<List<ThemeInfo>> = _themes.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    fun importTheme(uri: Uri, onResult: (Result<ThemeInfo>) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { withContext(Dispatchers.IO) { processor.importTheme(uri) } }
            _themes.value = store.loadAll()
            _busy.value = false
            onResult(result)
        }
    }

    fun convertBak(uri: Uri, onResult: (Result<ThemeInfo>) -> Unit) = importTheme(uri, onResult)

    fun translate(info: ThemeInfo, options: TranslationOptions, onResult: (Result<TranslationReport>) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _progress.value = 0
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    processor.translateTheme(info, options) { p -> _progress.value = p }
                }
            }
            if (result.isSuccess) {
                _themes.value = store.loadAll()
                onResult(Result.success(result.getOrThrow().second))
            } else {
                onResult(Result.failure(result.exceptionOrNull()!!))
            }
            _progress.value = 0
            _busy.value = false
        }
    }

    fun export(info: ThemeInfo, onResult: (Result<Uri>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { processor.exportToDownloads(info) } }
            onResult(result)
        }
    }

    fun delete(info: ThemeInfo) {
        store.delete(info)
        _themes.value = store.loadAll()
    }

}
