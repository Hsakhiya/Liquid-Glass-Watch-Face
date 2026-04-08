package com.example.myface

import LIQUID_GLASS_SHADER
import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
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
    16L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = true
) {

    private val glassShader = RuntimeShader(LIQUID_GLASS_SHADER)
    private val shaderPaint = Paint().apply { shader = glassShader }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 200f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private var debugMessage = "Waiting for phone..."
    private val debugPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private lateinit var backgroundBitmap: Bitmap
    private lateinit var textMaskBitmap: Bitmap
    private lateinit var textCanvas: Canvas
    private val hourFormatter = DateTimeFormatter.ofPattern("hh")
    private val minuteFormatter = DateTimeFormatter.ofPattern("mm")

    private var currentBlurRadius: Float = 5f

    // --- HELPER FUNCTION: Unpacks the Flutter watch_connectivity data ---
    private fun processFlutterData(rawData: ByteArray?) {
        if (rawData == null) return

        try {
            // Read the raw bytes back into a standard Java Map
            val inputStream = ObjectInputStream(ByteArrayInputStream(rawData))
            val map = inputStream.readObject() as? Map<String, Any>

            if (map != null) {
                val receivedKeys = map.keys.joinToString(", ")
                debugMessage = "Keys: $receivedKeys"
                CoroutineScope(Dispatchers.Main).launch { invalidate() }

                // 1. Process Background Image
                val imageBytes = map["background_bytes"] as? ByteArray
                if (imageBytes != null) {
                    debugMessage = "Got Image! Size: ${imageBytes.size}"
                    val newBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    CoroutineScope(Dispatchers.Main).launch {
                        backgroundBitmap = newBitmap
                        if (this@LiquidRenderer::textMaskBitmap.isInitialized) {
                            backgroundBitmap = Bitmap.createScaledBitmap(
                                backgroundBitmap,
                                textMaskBitmap.width,
                                textMaskBitmap.height,
                                true
                            )
                            glassShader.setInputShader(
                                "background",
                                BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                            )
                        }
                        invalidate()
                    }
                }

                // 2. Process Blur Slider
                val blurRadius = map["blur_radius"] as? Double
                if (blurRadius != null) {
                    currentBlurRadius = blurRadius.toFloat()
                    debugMessage = "Got Blur: $currentBlurRadius"
                    CoroutineScope(Dispatchers.Main).launch { invalidate() }
                }
            }
        } catch (e: Exception) {
            debugMessage = "Failed to parse map!"
            CoroutineScope(Dispatchers.Main).launch { invalidate() }
        }
    }
    // ------------------------------------------------------------------

    private val dataListener = DataClient.OnDataChangedListener { dataEvents ->
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                // Call our new helper function instead of using DataMapItem!
                processFlutterData(event.dataItem.data)
            }
        }
    }

    override suspend fun init() {
        val rawBackground = BitmapFactory.decodeResource(context.resources, R.drawable.mandir)
        backgroundBitmap = rawBackground

        val customTypeface = context.resources.getFont(R.font.schlbkb)
        textPaint.typeface = customTypeface

        // 1. Attach live listener
        Wearable.getDataClient(context.applicationContext).addListener(dataListener)

        // 2. Mailbox check on boot
        Wearable.getDataClient(context.applicationContext).dataItems.addOnSuccessListener { dataItems ->
            debugMessage = "Mailbox has ${dataItems.count} items"
            CoroutineScope(Dispatchers.Main).launch { invalidate() }

            for (item in dataItems) {
                // Call our new helper function here too!
                processFlutterData(item.data)
            }
        }.addOnFailureListener {
            debugMessage = "Mailbox check failed."
            CoroutineScope(Dispatchers.Main).launch { invalidate() }
        }
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        if (renderParameters.drawMode == DrawMode.AMBIENT) {
            canvas.drawColor(Color.BLACK)
            val hourText = zonedDateTime.format(hourFormatter)
            val minuteText = zonedDateTime.format(minuteFormatter)
            textPaint.style = Paint.Style.STROKE
            canvas.drawText(hourText, width / 2, height / 2 - 10f, textPaint)
            canvas.drawText(minuteText, width / 2, height / 2 + 160f, textPaint)
            textPaint.style = Paint.Style.FILL
            return
        }

        if (!this::textMaskBitmap.isInitialized || textMaskBitmap.width != bounds.width()) {
            textMaskBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            textCanvas = Canvas(textMaskBitmap)
            backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, bounds.width(), bounds.height(), true)
            glassShader.setFloatUniform("resolution", width, height)
            glassShader.setInputShader("background", BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }

        textMaskBitmap.eraseColor(Color.TRANSPARENT)

        if (currentBlurRadius > 0f) {
            textPaint.maskFilter = BlurMaskFilter(currentBlurRadius, BlurMaskFilter.Blur.NORMAL)
        } else {
            textPaint.maskFilter = null
        }

        val hourText = zonedDateTime.format(hourFormatter)
        val minuteText = zonedDateTime.format(minuteFormatter)

        textCanvas.drawText(hourText, width / 2, height / 2 - 10f, textPaint)
        textCanvas.drawText(minuteText, width / 2, height / 2 + 160f, textPaint)
        textPaint.maskFilter = null

        glassShader.setInputShader("textMask", BitmapShader(textMaskBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))

        canvas.drawRect(0f, 0f, width, height, shaderPaint)

        // VISUAL DEBUGGER
        canvas.drawText(debugMessage, width / 2f, 60f, debugPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {}
    override suspend fun createSharedAssets(): SharedAssets { return object : SharedAssets { override fun onDestroy() {} } }
}