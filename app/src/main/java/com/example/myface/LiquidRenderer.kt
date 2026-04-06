package com.example.myface

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class LiquidRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    16L, // Frame delay (approx 60fps)
    clearWithBackgroundTintBeforeRenderingHighlightLayer = true // <--- ADD THIS LINE
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
    private val hourFormatter = DateTimeFormatter.ofPattern("hh") // Use "hh" if you prefer 12-hour time
    private val minuteFormatter = DateTimeFormatter.ofPattern("mm")

    override suspend fun init() {
        // Load your background image from res/drawable
        val rawBackground = BitmapFactory.decodeResource(context.resources, R.drawable.mandir)
        // We will scale it in onRenderParametersChanged
        backgroundBitmap = rawBackground

        val customTypeface = context.resources.getFont(R.font.schlbkb)
        textPaint.typeface = customTypeface
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

        // Ensure our offscreen bitmap is initialized to the screen size
        if (!this::textMaskBitmap.isInitialized || textMaskBitmap.width != bounds.width()) {
            textMaskBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            textCanvas = Canvas(textMaskBitmap)

            // Scale background to fit screen
            backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, bounds.width(), bounds.height(), true)
            glassShader.setFloatUniform("resolution", width, height)
            glassShader.setInputShader("background", BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }

        // Clear the offscreen text canvas
        // Clear the offscreen text canvas
        textMaskBitmap.eraseColor(Color.TRANSPARENT)

        // --- NEW: Apply a blur to create a massive liquid footprint ---
        // The first number (25f) controls how WIDE the distortion area is.
        textPaint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)

        // Draw the hours and minutes onto the offscreen mask
        val hourText = zonedDateTime.format(hourFormatter)
        val minuteText = zonedDateTime.format(minuteFormatter)

        textCanvas.drawText(hourText, width / 2, height / 2 - 10f, textPaint)
        textCanvas.drawText(minuteText, width / 2, height / 2 + 160f, textPaint)

        // --- NEW: Remove the blur so Ambient mode stays sharp! ---
        textPaint.maskFilter = null

        // Bind the updated text mask to the shader
        glassShader.setInputShader("textMask", BitmapShader(textMaskBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))

        // 3. Draw the magic!
        // Drawing a rectangle over the whole screen using the shaderPaint
        // triggers the AGSL code for every pixel.
        canvas.drawRect(0f, 0f, width, height, shaderPaint)
    }

    // --- REQUIRED FUNCTION ADDED HERE ---
    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        // This is intentionally left blank.
        // It prevents the "does not implement abstract base class member" error.
    }
    // ------------------------------------

    override suspend fun createSharedAssets(): SharedAssets {
        return object : SharedAssets {
            override fun onDestroy() {}
        }
    }
}