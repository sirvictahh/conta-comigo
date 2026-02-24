package pt.contacomigo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Repositório simples para persistir ocorrências em armazenamento interno (JSON).
 *
 * Objetivo: manter complexidade baixa e cumprir persistência local.
 */
class OccurrenceRepository(private val context: Context) {

    private val fileName = "occurrences.json"

    /**
     * Devolve todas as ocorrências guardadas (ou lista vazia se ainda não existir ficheiro).
     */
    fun loadAll(): MutableList<Occurrence> {
        val file = getFile()
        if (!file.exists()) return mutableListOf()

        val jsonText = file.readText()
        if (jsonText.isBlank()) return mutableListOf()

        val array = JSONArray(jsonText)
        val result = mutableListOf<Occurrence>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Occurrence(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    createdAtEpochMillis = obj.getLong("createdAtEpochMillis")
                )
            )
        }

        return result
    }

    /**
     * Guarda a lista completa de ocorrências no ficheiro.
     */
    fun saveAll(items: List<Occurrence>) {
        val array = JSONArray()

        items.forEach { occ ->
            val obj = JSONObject().apply {
                put("id", occ.id)
                put("title", occ.title)
                put("latitude", occ.latitude)
                put("longitude", occ.longitude)
                put("createdAtEpochMillis", occ.createdAtEpochMillis)
            }
            array.put(obj)
        }

        val file = getFile()
        file.writeText(array.toString())
    }

    private fun getFile(): File {
        return File(context.filesDir, fileName)
    }
}