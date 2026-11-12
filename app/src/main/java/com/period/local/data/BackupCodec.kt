package com.period.local.data

import com.period.local.domain.Period
import com.period.local.domain.PeriodValidation
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object BackupCodec {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun decode(text: String, today: LocalDate): List<Period> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件过大，请选择 2 MB 以内的经期备份" }
        val tokener = JSONTokener(text.removePrefix("\uFEFF"))
        val root = tokener.nextValue()
        require(tokener.nextClean() == 0.toChar()) { "JSON 文件末尾包含无效内容" }
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> {
                require(root.getInt("schemaVersion") == 1) { "不支持该备份版本" }
                root.getJSONArray("records")
            }
            else -> throw IllegalArgumentException("请选择经期 JSON 备份")
        }
        require(array.length() <= 10000) { "记录数量超出支持范围" }
        val records = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val start = LocalDate.parse(row.getString("start_date"))
            val end = if (row.isNull("end_date")) null else LocalDate.parse(row.getString("end_date"))
            val id = if (row.has("id")) row.getString("id") else
                UUID.nameUUIDFromBytes("meiyou:$start".toByteArray(Charsets.UTF_8)).toString()
            Period(id, start, end)
        }
        PeriodValidation.validate(records, today)
        return records.sortedBy { it.start }
    }

    fun encode(records: List<Period>): String {
        val array = JSONArray()
        records.sortedBy { it.start }.forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("start_date", record.start.toString())
                put("end_date", record.end?.toString() ?: JSONObject.NULL)
            })
        }
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", Instant.now().toString())
            put("records", array)
        }.toString(2)
    }
}
