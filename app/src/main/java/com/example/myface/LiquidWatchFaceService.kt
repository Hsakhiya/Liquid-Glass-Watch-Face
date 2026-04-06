package com.example.myface

import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository

class LiquidWatchFaceService : WatchFaceService() {

    // --- FIX: Removed the 'suspend' keyword from this line ---
    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager {
        val backgroundSlot = ComplicationSlot.createBackgroundComplicationSlotBuilder(
            id = 100,
            canvasComplicationFactory = CanvasComplicationFactory { watchState, listener ->
                CanvasComplicationDrawable(
                    ComplicationDrawable(applicationContext),
                    watchState,
                    listener
                )
            },
            supportedTypes = listOf(ComplicationType.PHOTO_IMAGE),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy()
        ).build()

        return ComplicationSlotsManager(listOf(backgroundSlot), currentUserStyleRepository)
    }

    // (This one keeps suspend, as required by the OS)
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {

        val renderer = LiquidRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            complicationSlotsManager = complicationSlotsManager,
            canvasType = CanvasType.HARDWARE
        )

        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}