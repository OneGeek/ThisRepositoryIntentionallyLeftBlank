package com.tamawatch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tamawatch.tama
import com.tamawatch.ui.TamaActivity
import kotlinx.coroutines.runBlocking

/** Glanceable care status; tap opens the app. */
class CareTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val c = applicationContext.tama
        val pet = runBlocking { c.repository.load(); c.repository.pet.value }
        val line = if (pet == null) "Tap to hatch"
        else "${pet.name}   ♥${pet.stats.hungerHearts}  ☺${pet.stats.happyHearts}\n${pet.gp} GP · ${pet.stepsToday} steps"

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

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(launch).build())
            .addContent(
                Text.Builder(this, line)
                    .setTypographyName(Typography.TYPOGRAPHY_BODY1)
                    .build()
            ).build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(root)
            ).build()
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
