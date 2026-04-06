package com.example.myface

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.watchface.*
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.style.CurrentUserStyleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class LiquidRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
    private val complicationSlotsManager: ComplicationSlotsManager, // <--- ADDED MANAGER HERE
    canvasType: Int
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    16L, // Frame delay (approx 60fps)
    clearWithBackgroundTintBeforeRenderingHighlightLayer = true
) {

    // --- Shader & Paint Setup ---
    private val glassShader = RuntimeShader(LIQUID_GLASS_SHADER)
    private val shaderPaint = Paint().apply { shader = glassShader }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 185f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // --- Bitmaps ---
    private lateinit var backgroundBitmap: Bitmap
    private lateinit var textMaskBitmap: Bitmap
    private lateinit var textCanvas: Canvas
    private val hourFormatter = DateTimeFormatter.ofPattern("hh") // 12-hour time
    private val minuteFormatter = DateTimeFormatter.ofPattern("mm")

    override suspend fun init() {
        // Load your fallback background image
        val rawBackground = BitmapFactory.decodeResource(context.resources, R.drawable.mandir)
        backgroundBitmap = rawBackground

        // Load your custom font
        val customTypeface = context.resources.getFont(R.font.schlbkb)
        textPaint.typeface = customTypeface

        // --- NEW: THE BACKGROUND PHOTO LISTENER ---
        // This listens for when the user picks a photo via the watch customization menu
        CoroutineScope(Dispatchers.Main).launch {
            complicationSlotsManager.complicationSlots[100]?.complicationData?.collect { data ->
                if (data is PhotoImageComplicationData) {
                    val drawable = data.photoImage.loadDrawable(context)
                    if (drawable != null) {
                        val newPhoto = drawable.toBitmap()

                        // If the screen bounds are ready, scale and apply it immediately
                        if (this@LiquidRenderer::textMaskBitmap.isInitialized) {
                            backgroundBitmap = Bitmap.createScaledBitmap(newPhoto, textMaskBitmap.width, textMaskBitmap.height, true)
                            glassShader.setInputShader("background", BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
                            invalidate() // Force the watch to instantly redraw with the new photo!
                        } else {
                            // Otherwise, save it for the first render loop
                            backgroundBitmap = newPhoto
                        }
                    }
                }
            }
        }
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // 1. Handle Ambient Mode (Battery Saver - NO SHADERS!)
        if (renderParameters.drawMode == DrawMode.AMBIENT) {
            canvas.drawColor(Color.BLACK)
            val hourText = zonedDateTime.format(hourFormatter)
            val minuteText = zonedDateTime.format(minuteFormatter)

            textPaint.style = Paint.Style.STROKE // Outline only to prevent burn-in
            canvas.drawText(hourText, width / 2, height / 2 - 10f, textPaint)
            // Draw minutes below the middle
            canvas.drawText(minuteText, width / 2, height / 2 + 160f, textPaint)

            textPaint.style = Paint.Style.FILL
            return
        }

        // 2. Active Mode (Liquid Effect)
        if (!this::textMaskBitmap.isInitialized || textMaskBitmap.width != bounds.width()) {
            textMaskBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            textCanvas = Canvas(textMaskBitmap)

            // Scale background to fit screen
            backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, bounds.width(), bounds.height(), true)
            glassShader.setFloatUniform("resolution", width, height)
            glassShader.setInputShader("background", BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }

        // Clear the offscreen text canvas
        textMaskBitmap.eraseColor(Color.TRANSPARENT)

        // Apply a blur to create the liquid footprint
        textPaint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)

        // Draw the hours and minutes onto the offscreen mask
        val hourText = zonedDateTime.format(hourFormatter)
        val minuteText = zonedDateTime.format(minuteFormatter)

        textCanvas.drawText(hourText, width / 2, height / 2 - 10f, textPaint)
        textCanvas.drawText(minuteText, width / 2, height / 2 + 160f, textPaint)

        // Remove the blur so Ambient mode stays sharp!
        textPaint.maskFilter = null

        // Bind the updated text mask to the shader
        glassShader.setInputShader("textMask", BitmapShader(textMaskBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))

        // 3. Draw the magic!
        canvas.drawRect(0f, 0f, width, height, shaderPaint)
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        // Intentionally left blank to prevent abstract base class error.
    }

    override suspend fun createSharedAssets(): SharedAssets {
        return object : SharedAssets {
            override fun onDestroy() {}
        }
    }
}