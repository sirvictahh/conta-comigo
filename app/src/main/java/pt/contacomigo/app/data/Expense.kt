package pt.contacomigo.app.data

data class Expense(
    val id: String,
    var title: String,
    var amountCents: Long,
    var category: String,
    var latitude: Double?,
    var longitude: Double?,
    val createdAtEpochMillis: Long
) {
    fun hasLocation(): Boolean {
        return latitude != null && longitude != null
    }
}