package app.zelgray.pills_in_time.util

import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

interface NowProvider {
    fun currentLocalDateTime(): LocalDateTime
    fun currentLocalDate(): LocalDate = currentLocalDateTime().toLocalDate()
}

class SystemNowProvider @Inject constructor() : NowProvider {
    override fun currentLocalDateTime(): LocalDateTime = LocalDateTime.now()
}
