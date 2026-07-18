package app.zelgray.pills_in_time.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val DRUGS_LIST = "drugs"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val DRUG_DETAIL = "drugDetail/{drugId}"
    const val ADD_DRUG = "addDrug"
    const val EDIT_DRUG = "editDrug/{drugId}"
    const val ADD_STOCK = "addStock/{drugId}"
    const val EDIT_STOCK = "editStock/{drugId}/{stockId}"
    const val ADD_PERIOD = "addPeriod/{drugId}"
    const val EDIT_PERIOD = "editPeriod/{drugId}/{scheduleId}"
    const val ADD_HISTORY_ENTRY = "addHistoryEntry"
    const val EDIT_HISTORY_ENTRY = "editHistoryEntry/{logId}"

    const val ARG_DRUG_ID = "drugId"
    const val ARG_STOCK_ID = "stockId"
    const val ARG_SCHEDULE_ID = "scheduleId"
    const val ARG_LOG_ID = "logId"

    fun drugDetail(drugId: Long) = "drugDetail/$drugId"
    fun editDrug(drugId: Long) = "editDrug/$drugId"
    fun addStock(drugId: Long) = "addStock/$drugId"
    fun editStock(drugId: Long, stockId: Long) = "editStock/$drugId/$stockId"
    fun addPeriod(drugId: Long) = "addPeriod/$drugId"
    fun editPeriod(drugId: Long, scheduleId: Long) = "editPeriod/$drugId/$scheduleId"
    fun editHistoryEntry(logId: Long) = "editHistoryEntry/$logId"

    val TAB_ROUTES = setOf(HOME, DRUGS_LIST, HISTORY, SETTINGS)
}
