package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.validateBudget
import com.joaoeoneves.fintrack.data.local.validateCategory
import com.joaoeoneves.fintrack.data.local.validateExpense
import com.joaoeoneves.fintrack.data.local.validateIncome
import com.joaoeoneves.fintrack.data.local.validateMonthSettings
import com.joaoeoneves.fintrack.data.local.validatePlannedIncome
import com.joaoeoneves.fintrack.data.local.validateRecurring
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of [BackupCodec.decode]. */
sealed interface BackupDecodeResult {
    /** [skippedCount] records were dropped (failed validation or duplicate id). */
    data class Success(
        val document: BackupDocument,
        val skippedCount: Int,
    ) : BackupDecodeResult

    data class UnsupportedVersion(
        val found: Int,
    ) : BackupDecodeResult

    data class Invalid(
        val reason: String,
    ) : BackupDecodeResult
}

/** Pure encoder/decoder of backup files; no I/O. */
object BackupCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    fun decode(text: String): BackupDecodeResult {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: IllegalArgumentException) {
                // SerializationException and "not an object" both land here.
                return BackupDecodeResult.Invalid(e.message ?: "Not a JSON object")
            }
        return checkHeader(root) ?: decodeBody(root)
    }

    /** A failure result when the format marker or version is wrong, else `null`. */
    private fun checkHeader(root: JsonObject): BackupDecodeResult? {
        val format = root["format"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        val version = root["schemaVersion"]?.let { runCatching { it.jsonPrimitive.int }.getOrNull() }
        return when {
            format != BACKUP_FORMAT -> BackupDecodeResult.Invalid("Not a FinTrack backup")
            version == null || version < 1 -> BackupDecodeResult.Invalid("Missing or invalid schemaVersion")
            version > BACKUP_SCHEMA_VERSION -> BackupDecodeResult.UnsupportedVersion(version)
            else -> null
        }
    }

    private fun decodeBody(root: JsonObject): BackupDecodeResult {
        val document =
            try {
                json.decodeFromJsonElement(BackupDocument.serializer(), root)
            } catch (e: IllegalArgumentException) {
                return BackupDecodeResult.Invalid(e.message ?: "Malformed backup")
            }
        // Per-version migrations: 1 -> 2 added plannedIncome, 2 -> 3 recurring and 3 -> 4 monthSettings and
        // the income-line basis/frequency fields (absent = empty / defaults).
        return when (document.schemaVersion) {
            in 1 until BACKUP_SCHEMA_VERSION -> sanitize(document.copy(schemaVersion = BACKUP_SCHEMA_VERSION))
            BACKUP_SCHEMA_VERSION -> sanitize(document)
            else -> BackupDecodeResult.UnsupportedVersion(document.schemaVersion)
        }
    }

    /** Drops invalid and duplicate records; categories first, as the others reference them. */
    private fun sanitize(document: BackupDocument): BackupDecodeResult.Success {
        val categories = keepValid(document.categories, { it.id }) { validateCategory(it) }
        val known = categoryIdsWithBuiltIns(categories)
        val budgets =
            keepValid(document.budgets, { it.categoryId }) { validateBudget(it.categoryId, it.limitCents, known) }
        val expenses = keepValid(document.expenses, { it.id }) { validateExpense(it, known) }
        val income = keepValid(document.income, { it.id }) { validateIncome(it) }
        val plannedIncome = keepValid(document.plannedIncome, { it.id }) { validatePlannedIncome(it) }
        val recurring = keepValid(document.recurring, { it.id }) { validateRecurring(it, known) }
        val monthSettings = keepValid(document.monthSettings, { it.month }) { validateMonthSettings(it) }
        val skipped =
            (document.categories.size - categories.size) + (document.budgets.size - budgets.size) +
                (document.expenses.size - expenses.size) + (document.income.size - income.size) +
                (document.plannedIncome.size - plannedIncome.size) + (document.recurring.size - recurring.size) +
                (document.monthSettings.size - monthSettings.size)
        return BackupDecodeResult.Success(
            document.copy(
                categories = categories,
                budgets = budgets,
                expenses = expenses,
                income = income,
                plannedIncome = plannedIncome,
                recurring = recurring,
                monthSettings = monthSettings,
            ),
            skipped,
        )
    }

    private fun categoryIdsWithBuiltIns(categories: List<LocalCategory>): Set<String> =
        BuiltInCategories.all.mapTo(mutableSetOf()) { it.id.value } + categories.map { it.id }

    private inline fun <T> keepValid(
        records: List<T>,
        id: (T) -> String,
        validate: (T) -> Unit,
    ): List<T> {
        val seen = mutableSetOf<String>()
        return records.filter { record ->
            val valid =
                try {
                    validate(record)
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            valid && seen.add(id(record))
        }
    }
}
