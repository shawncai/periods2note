package com.period.local

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.period.local.data.BackupCodec
import com.period.local.data.ImportPreview
import com.period.local.data.PeriodRepository
import com.period.local.domain.Period
import com.period.local.domain.Prediction
import com.period.local.domain.PredictionEngine
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScreenState(
    val records: List<Period> = emptyList(),
    val prediction: Prediction? = null,
    val today: LocalDate = LocalDate.now(),
    val ready: Boolean = false,
)

class PeriodViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PeriodRepository = (application as PeriodApplication).repository,
) : AndroidViewModel(application) {
    private val today = MutableStateFlow(LocalDate.now())
    private val ready = MutableStateFlow(false)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _startupError = MutableStateFlow<String?>(null)
    val startupError = _startupError.asStateFlow()
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview = _importPreview.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    val state = combine(repository.records, today, ready) { records, date, initialized ->
        ScreenState(records, PredictionEngine.calculate(records), date, initialized)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenState())

    init { initialize() }

    fun refreshToday() { today.value = LocalDate.now() }

    fun initialize() {
        if (_busy.value) return
        _busy.value = true
        _startupError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Public builds start empty; users import their own records locally.
                    repository.initialize("[]", LocalDate.now())
                }
                ready.value = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _startupError.value = "本地数据加载失败，请重试。${error.message.orEmpty()}"
            } finally { _busy.value = false }
        }
    }

    private fun operate(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _messages.emit(error.message ?: "操作失败，请重试")
            } finally { _busy.value = false }
        }
    }

    fun save(period: Period, original: Period?, onSaved: () -> Unit) = operate {
        repository.save(period, original, LocalDate.now())
        refreshToday()
        _messages.emit("记录已保存，预测已更新")
        onSaved()
    }

    fun startToday() = operate {
        val date = LocalDate.now()
        repository.startToday(date)
        refreshToday()
        _messages.emit("已记录今天开始，预测已更新")
    }

    fun endToday() = operate {
        val date = LocalDate.now()
        repository.endToday(date)
        refreshToday()
        _messages.emit("已记录今天结束")
    }

    fun export(uri: Uri) = operate {
        withContext(Dispatchers.IO) {
            val text = BackupCodec.encode(repository.all())
            val resolver = getApplication<Application>().contentResolver
            val output = requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入所选文件" }
            output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        }
        _messages.emit("备份已保存")
    }

    fun readImport(uri: Uri) = operate {
        val records = withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val stream = requireNotNull(resolver.openInputStream(uri)) { "无法读取所选文件" }
            val bytes = stream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count != -1) {
                    require(output.size() + count <= BackupCodec.MAX_BYTES) { "文件过大，请选择 2 MB 以内的经期备份" }
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                output.toByteArray()
            }
            try { BackupCodec.decode(bytes.toString(Charsets.UTF_8), LocalDate.now()) }
            catch (error: org.json.JSONException) { throw IllegalArgumentException("备份格式不正确，请选择完整的 JSON 文件", error) }
            catch (error: java.time.format.DateTimeParseException) { throw IllegalArgumentException("备份日期格式应为 YYYY-MM-DD", error) }
        }
        _importPreview.value = repository.preview(records)
    }

    fun dismissImport() { if (!_busy.value) _importPreview.value = null }

    fun confirmImport(replacements: Set<String>) = operate {
        val preview = requireNotNull(_importPreview.value) { "请重新选择导入文件" }
        repository.import(preview, replacements, LocalDate.now())
        _importPreview.value = null
        refreshToday()
        _messages.emit("导入完成，预测已更新")
    }
}
