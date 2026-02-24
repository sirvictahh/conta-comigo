package pt.contacomigo.app.data

/**
 * Modelo de dados de uma ocorrência colocada no mapa.
 *
 * @param id Identificador único (ex.: UUID).
 * @param title Título/descrição curta da ocorrência.
 * @param latitude Latitude onde a ocorrência foi registada.
 * @param longitude Longitude onde a ocorrência foi registada.
 * @param createdAtEpochMillis Momento de criação (epoch millis), útil para ordenação/auditoria.
 */
data class Occurrence(
    val id: String,
    var title: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtEpochMillis: Long
)