package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.upgradedToStartMonths
import com.joaoeoneves.fintrack.data.local.validateAutomation
import com.joaoeoneves.fintrack.data.local.validateBudget
import com.joaoeoneves.fintrack.data.local.validateCategory
import com.joaoeoneves.fintrack.data.local.validateCategoryGroup
import com.joaoeoneves.fintrack.data.local.validateExpense
import com.joaoeoneves.fintrack.data.local.validateGoal
import com.joaoeoneves.fintrack.data.local.validateIncome
import com.joaoeoneves.fintrack.data.local.validateMonthSettings
import com.joaoeoneves.fintrack.data.local.validatePlannedIncome
import com.joaoeoneves.fintrack.data.local.validateRecurring
import com.joaoeoneves.fintrack.data.local.validateSalaryProfile
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.YearMonth
import java.time.ZoneId

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
        // Per-version migrations: 1 -> 2 added plannedIncome, 2 -> 3 recurring, 3 -> 4 monthSettings and the
        // income-line basis/frequency fields and 4 -> 5 categoryGroups, category groupId and budget skipMonths
        // (absent = empty / defaults) and 5 -> 6 the monthSettings daysOff field (absent = 0) and
        // 6 -> 7 the plannedIncome earmark fields (absent = none) and 7 -> 8 the salaryProfile and plannedIncome
        // managedKey (absent = none) and 8 -> 9 the goals (absent = empty) and
        // 9 -> 10 salaryProfile extras (absent = migrated from the legacy fields on read) and
        // 11 -> 12 the automation settings (absent = none) and
        // 12 -> 13 the salary extra amountScope (absent = TOTAL) and
        // 15 -> 16 plannedIncome autoRecord/payDay and automation lineThrough (absent = off / last working day / none).
        // 16 -> 17 recurring skipped/pausedAt (absent = empty / none).
        // 17 -> 18 automation salaryPaysNextMonth (absent = not chosen).
        // 18 -> 19 plan startMonth / versions (absent = the earliest month with data, see upgradedToStartMonths).
        // 19 -> 20 salaryProfile sector/mealPayment/otherDeductionsPpm, plannedIncome payDayEarlierIfClosed, expense
        // paidWithMealCard and category mealCardByDefault (absent = private / card / 0 / off / off / off).
        return when (document.schemaVersion) {
            in 1 until BACKUP_SCHEMA_VERSION -> sanitize(document.upgradedToStartMonths())
            BACKUP_SCHEMA_VERSION -> sanitize(document)
            else -> BackupDecodeResult.UnsupportedVersion(document.schemaVersion)
        }
    }

    /** Drops invalid and duplicate records; categories first, as the others reference them. */
    private fun sanitize(document: BackupDocument): BackupDecodeResult.Success {
        val categories = keepValid(document.categories, { it.id }) { validateCategory(it) }
        val known = categoryIdsWithBuiltIns(categories)
        val budgets =
            keepValid(document.budgets, { it.categoryId }) {
                validateBudget(it, known)
            }
        val expenses = keepValid(document.expenses, { it.id }) { validateExpense(it, known) }
        val income = keepValid(document.income, { it.id }) { validateIncome(it) }
        val salaryProfile = document.salaryProfile?.takeIf { isValidProfile(it) }
        val plannedIncome =
            keepValid(document.plannedIncome, { it.id }) { validatePlannedIncome(it) }
                // Managed lines without their profile would be read-only forever: they become plain lines.
                .map { if (salaryProfile == null) it.copy(managedKey = null) else it }
        val recurring = keepValid(document.recurring, { it.id }) { validateRecurring(it, known) }
        val monthSettings = keepValid(document.monthSettings, { it.month }) { validateMonthSettings(it) }
        val categoryGroups = keepValid(document.categoryGroups, { it.id }) { validateCategoryGroup(it) }
        val goals = keepValid(document.goals, { it.id }) { validateGoal(it) }
        val automation = document.automation?.takeIf { runCatching { validateAutomation(it) }.isSuccess }
        val skipped =
            (document.categories.size - categories.size) + (document.budgets.size - budgets.size) +
                (document.expenses.size - expenses.size) + (document.income.size - income.size) +
                (document.plannedIncome.size - plannedIncome.size) + (document.recurring.size - recurring.size) +
                (document.monthSettings.size - monthSettings.size) +
                (document.categoryGroups.size - categoryGroups.size) + (document.goals.size - goals.size) +
                (if (document.salaryProfile != null && salaryProfile == null) 1 else 0) +
                (if (document.automation != null && automation == null) 1 else 0)
        return BackupDecodeResult.Success(
            document.copy(
                categories = categories,
                budgets = budgets,
                expenses = expenses,
                income = income,
                plannedIncome = plannedIncome,
                recurring = recurring,
                monthSettings = monthSettings,
                categoryGroups = categoryGroups,
                salaryProfile = salaryProfile,
                goals = goals,
                automation = automation,
            ),
            skipped,
        )
    }

    private fun isValidProfile(profile: LocalSalaryProfile): Boolean =
        try {
            validateSalaryProfile(profile)
            true
        } catch (_: IllegalArgumentException) {
            false
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

/** The 18 -> 19 upgrade of a backup: the same start-month rule as the guest file (see [upgradedToStartMonths]). */
internal fun BackupDocument.upgradedToStartMonths(
    now: YearMonth = YearMonth.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): BackupDocument {
    val upgraded =
        LocalDataDocument(
            expenses = expenses,
            income = income,
            budgets = budgets,
            plannedIncome = plannedIncome,
            categoryGroups = categoryGroups,
        ).upgradedToStartMonths(now, zone)
    return copy(
        schemaVersion = BACKUP_SCHEMA_VERSION,
        budgets = upgraded.budgets,
        plannedIncome = upgraded.plannedIncome,
        categoryGroups = upgraded.categoryGroups,
    )
}
