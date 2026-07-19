package app.zelgray.pills_in_time.domain.model

/** Result of writing/editing/deleting an IntakeLog when TAKEN status triggers real stock consumption. */
sealed interface RecordLogResult {
    data object Success : RecordLogResult
    data object InsufficientStock : RecordLogResult
}
