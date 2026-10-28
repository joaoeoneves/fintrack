package com.joaoeoneves.fintrack.ui.settings

/** What the data group can do; each row maps to one action. */
data class DataSectionActions(
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onImportCsv: () -> Unit,
    val onExportCsv: () -> Unit,
    val onDeleteAll: () -> Unit,
)
