package pt.contacomigo.app.data

data class Expense(
    val id: String,
    var title: String,
    var amountCents: Long,
    var category: String,
    val createdAtEpochMillis: Long
)