package com.tamawatch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.tamawatch.core.model.Stats
import com.tamawatch.tama

/** Surfaces the pet's Hunger on a watch face (RANGED_VALUE or SHORT_TEXT). */
class CareComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> ranged(3, "Tama")
        ComplicationType.SHORT_TEXT -> shortText(3)
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val c = applicationContext.tama
        c.repository.load()
        val pet = c.repository.pet.value
        val hearts = pet?.stats?.hungerHearts ?: 0
        return when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> ranged(hearts, pet?.name ?: "Tama")
            else -> shortText(hearts)
        }
    }

    private fun ranged(hearts: Int, name: String): RangedValueComplicationData {
        val desc = PlainComplicationText.Builder("$name hunger").build()
        return RangedValueComplicationData.Builder(
            value = hearts.toFloat(), min = 0f, max = Stats.HEARTS.toFloat(),
            contentDescription = desc,
        ).setText(PlainComplicationText.Builder("♥$hearts").build()).build()
    }

    private fun shortText(hearts: Int): ShortTextComplicationData {
        val desc = PlainComplicationText.Builder("Tama hunger").build()
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("♥$hearts").build(),
            contentDescription = desc,
        ).build()
    }
}
