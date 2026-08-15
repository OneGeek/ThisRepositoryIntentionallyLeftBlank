package com.tamawatch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tamawatch.tama
import com.tamawatch.ui.TamaActivity
import kotlinx.coroutines.runBlocking

/** Glanceable care status; tap opens the app. Uses core ProtoLayout only. */
class CareTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val c = applicationContext.tama
        val pet = runBlocking { c.repository.load(); c.repository.pet.value }
        val line = if (pet == null) "TamaWatch — tap to hatch"
        else "${pet.name}   hunger ${pet.stats.hungerHearts}/4   happy ${pet.stats.happyHearts}/4   ·   ${pet.gp} GP"

        val launch = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(TamaActivity::class.java.name)
                            .build()
                    ).build()
            ).build()

        val text = LayoutElementBuilders.Text.Builder()
            .setText(line)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(15f))
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build()
            ).build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(launch).build())
            .addContent(text)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
        )

    companion object { private const val RES_VERSION = "1" }
}
