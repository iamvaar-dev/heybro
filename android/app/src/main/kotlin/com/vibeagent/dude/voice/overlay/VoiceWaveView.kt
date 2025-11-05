package com.vibeagent.dude.voice.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.ColorUtils
import android.os.Handler
import android.os.Looper
import kotlin.math.*

/**
 * VoiceWaveView displays an animated glow pattern at the bottom of the screen
 * during voice interactions. Inspired by modern AI interfaces.
 */
class VoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Modern tri-color scheme inspired by Gemini
    private val colorPrimary = Color.parseColor("#00E5FF")   // Electric Cyan
    private val colorSecondary = Color.parseColor("#7C4DFF")  // Deep Purple  
    private val colorTertiary = Color.parseColor("#FF4081")   // Hot Pink
    private val colorYellow = Color.parseColor("#00FF88")     // Neon Green (replacing yellow)
    
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private var animationProgress = 0f
    private var pulseAnimator: ValueAnimator? = null
    private var shimmerAnimator: ValueAnimator? = null
    private var shimmerProgress = 0f
    
    // Additional animators for enhanced effects
    private var waveAnimator: ValueAnimator? = null
    private var wavePhase = 0f
    private var orbAnimator: ValueAnimator? = null
    private var orbPhase = 0f
    
    // Enhanced glow configuration
    private val glowHeight = 48f
    private val glowExtendHeight = 120f
    
    // Particle system
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float,
        var size: Float,
        var color: Int
    )
    private val particles = mutableListOf<Particle>()
    private val maxParticles = 20
    
    // Wave path for smooth curves
    private val wavePath = Path()
    
    // Error message state (keeping exact same implementation)
    private var showErrorMessage = false
    private var errorMessage = ""
    private var errorAlpha = 0f
    private var errorAnimator: ValueAnimator? = null
    private val errorHandler = Handler(Looper.getMainLooper())
    private var hideErrorRunnable: Runnable? = null
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val errorBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1F1F1F")
    }
    
    // Additional paints for enhanced effects
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    init {
        // Enable hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw the enhanced glow animation at the bottom
        drawWaveAnimation(canvas)
        
        // Draw additional visual effects
        drawFloatingOrbs(canvas)
        drawParticles(canvas)
        drawTopGlow(canvas)
        
        // Draw error message if needed (keeping exact same implementation)
        if (showErrorMessage) {
            drawErrorMessage(canvas)
        }
    }
    
    private fun drawWaveAnimation(canvas: Canvas) {
        val bottomY = height.toFloat()
        val width = width.toFloat()
        
        // Draw multiple wave layers for depth
        for (layer in 0..2) {
            val layerOffset = layer * 20f
            val layerAlpha = 1f - (layer * 0.3f)
            
            wavePath.reset()
            wavePath.moveTo(0f, bottomY)
            
            // Create smooth wave using sine functions
            val segments = 20
            for (i in 0..segments) {
                val x = (width / segments) * i
                val waveOffset = sin((wavePhase + i * 15f + layer * 60f) * PI / 180f) * 30f
                val y = bottomY - glowHeight - layerOffset + waveOffset.toFloat()
                
                if (i == 0) {
                    wavePath.lineTo(x, y)
                } else {
                    val prevX = (width / segments) * (i - 1)
                    val midX = (prevX + x) / 2
                    val prevWaveOffset = sin((wavePhase + (i-1) * 15f + layer * 60f) * PI / 180f) * 30f
                    val prevY = bottomY - glowHeight - layerOffset + prevWaveOffset.toFloat()
                    wavePath.quadTo(prevX, prevY, midX, y)
                }
            }
            
            wavePath.lineTo(width, bottomY)
            wavePath.close()
            
            // Create tri-color gradient
            val colors = when(layer) {
                0 -> intArrayOf(
                    ColorUtils.setAlphaComponent(colorPrimary, (150 * layerAlpha).toInt()),
                    ColorUtils.setAlphaComponent(colorSecondary, (100 * layerAlpha).toInt()),
                    ColorUtils.setAlphaComponent(colorTertiary, (50 * layerAlpha).toInt()),
                    Color.TRANSPARENT
                )
                1 -> intArrayOf(
                    ColorUtils.setAlphaComponent(colorSecondary, (120 * layerAlpha).toInt()),
                    ColorUtils.setAlphaComponent(colorTertiary, (80 * layerAlpha).toInt()),
                    ColorUtils.setAlphaComponent(colorPrimary, (40 * layerAlpha).toInt()),
                    Color.TRANSPARENT
                )
                else -> intArrayOf(
                    ColorUtils.setAlphaComponent(colorTertiary, (100 * layerAlpha).toInt()),
                    ColorUtils.setAlphaComponent(colorPrimary, (60 * layerAlpha).toInt()),
                    ColorUtils.setAlphaComponent(colorSecondary, (30 * layerAlpha).toInt()),
                    Color.TRANSPARENT
                )
            }
            
            val flowingGradient = LinearGradient(
                0f, bottomY,
                0f, bottomY - glowHeight - layerOffset - 30f,
                colors,
                floatArrayOf(0f, 0.3f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            
            glowPaint.shader = flowingGradient
            
            // Apply breathing effect
            val breathScale = 0.9f + 0.2f * sin(animationProgress * 0.5 * PI / 180f).toFloat()
            glowPaint.alpha = (200 * layerAlpha * breathScale).toInt()
            
            canvas.drawPath(wavePath, glowPaint)
        }
        
        // Original shimmer effect with enhanced colors
        val shimmerWidth = width * 0.4f
        val shimmerX = (shimmerProgress * (width + shimmerWidth)) - shimmerWidth
        
        val shimmerGradient = LinearGradient(
            shimmerX, bottomY - glowHeight,
            shimmerX + shimmerWidth, bottomY - glowHeight,
            intArrayOf(
                Color.TRANSPARENT,
                ColorUtils.setAlphaComponent(colorPrimary, 60),
                ColorUtils.setAlphaComponent(Color.WHITE, 100),
                ColorUtils.setAlphaComponent(colorSecondary, 60),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        
        shimmerPaint.shader = shimmerGradient
        val glowRect = RectF(0f, bottomY - glowHeight, width, bottomY)
        canvas.drawRect(glowRect, shimmerPaint)
        
        // Add pulse layer with tri-color effect
        val pulseAlpha = (40 * (0.5f + 0.5f * sin(animationProgress * 0.25 * PI / 180f))).toInt()
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        
        val pulseGradient = LinearGradient(
            0f, bottomY,
            width, bottomY,
            intArrayOf(
                ColorUtils.setAlphaComponent(colorPrimary, pulseAlpha),
                ColorUtils.setAlphaComponent(colorSecondary, pulseAlpha),
                ColorUtils.setAlphaComponent(colorTertiary, pulseAlpha)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        
        pulsePaint.shader = pulseGradient
        val pulseScale = 0.9f + 0.2f * sin(animationProgress * 0.25 * PI / 180f).toFloat()
        canvas.save()
        canvas.scale(1f, pulseScale, width / 2f, bottomY)
        canvas.drawRect(glowRect, pulsePaint)
        canvas.restore()
    }
    
    private fun drawFloatingOrbs(canvas: Canvas) {
        val bottomY = height.toFloat()
        val width = width.toFloat()
        
        // Draw floating light orbs
        for (i in 0..3) {
            val orbX = width * (0.2f + 0.6f * ((sin((orbPhase + i * 90f) * PI / 180f) + 1f) / 2f)).toFloat()
            val orbY = bottomY - glowHeight - 20f - (sin((orbPhase * 2 + i * 60f) * PI / 180f) * 25f).toFloat()
            val orbSize = 4f + (sin((animationProgress + i * 45f) * PI / 180f) * 2f).toFloat()
            
            val orbColor = when(i % 3) {
                0 -> colorPrimary
                1 -> colorSecondary
                else -> colorTertiary
            }
            
            // Outer glow
            val glowGradient = RadialGradient(
                orbX, orbY, orbSize * 4f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(orbColor, 100),
                    ColorUtils.setAlphaComponent(orbColor, 40),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            
            blurPaint.shader = glowGradient
            canvas.drawCircle(orbX, orbY, orbSize * 3f, blurPaint)
            
            // Inner bright core
            orbPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 180)
            canvas.drawCircle(orbX, orbY, orbSize * 0.5f, orbPaint)
        }
    }
    
    private fun drawParticles(canvas: Canvas) {
        // Update and draw particles
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            
            particle.y += particle.vy
            particle.x += particle.vx
            particle.vy -= 0.05f
            particle.alpha -= 0.02f
            
            if (particle.alpha <= 0 || particle.y < 0) {
                iterator.remove()
                continue
            }
            
            orbPaint.color = ColorUtils.setAlphaComponent(particle.color, (particle.alpha * 255).toInt())
            canvas.drawCircle(particle.x, particle.y, particle.size, orbPaint)
        }
        
        // Generate new particles
        if (particles.size < maxParticles && Math.random() < 0.1) {
            val colors = listOf(colorPrimary, colorSecondary, colorTertiary, colorYellow)
            particles.add(Particle(
                x = (Math.random() * width).toFloat(),
                y = height.toFloat() - glowHeight,
                vx = ((Math.random() - 0.5) * 1.5).toFloat(),
                vy = (-Math.random() * 3 - 1).toFloat(),
                alpha = 0.8f,
                size = (Math.random() * 2 + 1).toFloat(),
                color = colors.random()
            ))
        }
    }
    
    private fun drawTopGlow(canvas: Canvas) {
        val bottomY = height.toFloat()
        val width = width.toFloat()
        
        // Create sharp edge glow
        val edgeGradient = LinearGradient(
            0f, bottomY - 3f,
            width, bottomY - 3f,
            intArrayOf(
                ColorUtils.setAlphaComponent(colorPrimary, 200),
                ColorUtils.setAlphaComponent(colorSecondary, 200),
                ColorUtils.setAlphaComponent(colorTertiary, 200)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = edgeGradient
            alpha = (150 * (0.7f + 0.3f * sin(animationProgress * PI / 180f))).toInt()
        }
        
        canvas.drawRect(0f, bottomY - 2f, width, bottomY, edgePaint)
    }
    
    private fun drawErrorMessage(canvas: Canvas) {
        if (errorMessage.isEmpty() || errorAlpha <= 0f) return
        
        // Calculate dimensions
        val padding = 24f
        val textWidth = textPaint.measureText(errorMessage)
        val textHeight = textPaint.textSize
        val boxWidth = textWidth + padding * 2
        val boxHeight = textHeight + padding * 2
        
        // Position above the glow animation
        val left = (width - boxWidth) / 2
        val bottom = height - glowExtendHeight - 20f
        val top = bottom - boxHeight
        val right = left + boxWidth
        
        // Apply alpha to all paints
        val currentAlpha = (errorAlpha * 255).toInt()
        errorBgPaint.alpha = currentAlpha
        textPaint.alpha = currentAlpha
        
        // Draw main background
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 16f, 16f, errorBgPaint)
        
        // Draw subtle tricolor accent bar at the top
        val accentHeight = 4f
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            alpha = currentAlpha
        }
        
        val segmentWidth = boxWidth / 3
        val colors = arrayOf(colorPrimary, colorSecondary, colorTertiary)
        
        for (i in 0 until 3) {
            accentPaint.color = colors[i]
            val segmentLeft = left + i * segmentWidth
            val segmentRight = if (i == 2) right else segmentLeft + segmentWidth
            
            val accentRect = RectF(segmentLeft, top, segmentRight, top + accentHeight)
            if (i == 0) {
                canvas.drawRoundRect(accentRect, 16f, 0f, accentPaint)
            } else if (i == 2) {
                canvas.drawRoundRect(accentRect, 0f, 16f, accentPaint)
            } else {
                canvas.drawRect(accentRect, accentPaint)
            }
        }
        
        // Draw text centered
        val textY = top + padding + textHeight * 0.75f
        canvas.drawText(errorMessage, width / 2f, textY, textPaint)
    }
    
    fun showError(message: String, autoHideDelayMs: Long = 3000) {
        errorMessage = message
        showErrorMessage = true
        
        // Cancel any existing animations and timers
        errorAnimator?.cancel()
        hideErrorRunnable?.let { errorHandler.removeCallbacks(it) }
        
        // Fade in animation
        errorAnimator = ValueAnimator.ofFloat(errorAlpha, 1f).apply {
            duration = 300
            addUpdateListener { animator ->
                errorAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        // Auto-hide after delay
        hideErrorRunnable = Runnable {
            hideError()
        }
        errorHandler.postDelayed(hideErrorRunnable!!, autoHideDelayMs)
    }
    
    fun hideError() {
        hideErrorRunnable?.let { errorHandler.removeCallbacks(it) }
        
        // Fade out animation
        errorAnimator?.cancel()
        errorAnimator = ValueAnimator.ofFloat(errorAlpha, 0f).apply {
            duration = 300
            addUpdateListener { animator ->
                errorAlpha = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    showErrorMessage = false
                    errorMessage = ""
                }
            })
            start()
        }
    }
    
    fun startAnimation() {
        stopAnimation()
        
        // Main glow animation
        pulseAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                invalidate()
            }
            
            start()
        }
        
        // Shimmer animation
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                shimmerProgress = animator.animatedValue as Float
                invalidate()
            }
            
            start()
        }
        
        // Wave animation for smooth flow
        waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                wavePhase = animator.animatedValue as Float
            }
            
            start()
        }
        
        // Orb animation
        orbAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                orbPhase = animator.animatedValue as Float
            }
            
            start()
        }
    }
    
    fun stopAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        waveAnimator?.cancel()
        waveAnimator = null
        orbAnimator?.cancel()
        orbAnimator = null
        
        animationProgress = 0f
        shimmerProgress = 0f
        wavePhase = 0f
        orbPhase = 0f
        particles.clear()
        
        invalidate()
    }
    
    // Custom size measurement for the glow view
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Set a fixed height for the glow view
        val height = 80
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            height
        )
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
        
        // Clean up error-related resources
        errorAnimator?.cancel()
        hideErrorRunnable?.let { errorHandler.removeCallbacks(it) }
    }
}