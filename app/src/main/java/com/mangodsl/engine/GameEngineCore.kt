package com.mangodsl.engine

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * محرك ألعاب 2D عالي الأداء للأندرويد
 * يتولى حلقة اللعبة (Game Loop)، الرسم، الكشف عن التصادمات، ومدخلات اللمس.
 */

enum class ScriptType {
    ARABIC_DSL,
    PYTHON,
    NONE
}

data class Vector2(var x: Float = 0f, var y: Float = 0f) {
    fun set(newX: Float, newY: Float) {
        x = newX
        y = newY
    }
    fun add(dx: Float, dy: Float) {
        x += dx
        y += dy
    }
}

/**
 * تمثيل الكائن داخل مشهد اللعبة (Entity / Sprite)
 */
class GameEntity(
    var id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "كائن_جديد",
    var tag: String = "افتراضي"
) {
    val position = Vector2(0f, 0f)
    val velocity = Vector2(0f, 0f)
    var width: Float = 64f
    var height: Float = 64f
    var rotation: Float = 0f
    var scaleX: Float = 1f
    var scaleY: Float = 1f

    // الخصائص الفيزيائية
    var isSolid: Boolean = false
    var affectedByGravity: Boolean = false
    var isGrounded: Boolean = false
    var isVisible: Boolean = true
    var isActive: Boolean = true
    var health: Float = 100f

    // المظهر والرسوم
    var color: Int = Color.parseColor("#4CAF50")
    var spriteName: String = ""
    @Transient var spriteBitmap: Bitmap? = null

    // نصوص البرمجة
    var scriptType: ScriptType = ScriptType.ARABIC_DSL
    var scriptCode: String = ""

    // مخزن الخصائص المخصصة وحقيبة الكائن (Inventory & Custom Attributes)
    val inventory = mutableListOf<String>()
    val attributes = ConcurrentHashMap<String, Any>()

    // مستطيل التصادم AABB
    fun getBoundingBox(): RectF {
        return RectF(position.x, position.y, position.x + width * scaleX, position.y + height * scaleY)
    }

    fun hasItem(itemName: String): Boolean {
        val clean = itemName.trim().lowercase()
        return inventory.any { it.trim().lowercase() == clean }
    }

    fun addItem(itemName: String) {
        val clean = itemName.trim()
        if (!inventory.contains(clean)) {
            inventory.add(clean)
        }
    }

    fun removeItem(itemName: String): Boolean {
        val clean = itemName.trim().lowercase()
        val item = inventory.firstOrNull { it.trim().lowercase() == clean }
        return if (item != null) {
            inventory.remove(item)
            true
        } else {
            false
        }
    }
}

/**
 * تمثيل مشهد اللعبة (Scene)
 */
class GameScene(var name: String = "المشهد_الرئيسي") {
    var backgroundColor: Int = Color.parseColor("#1A1D24")
    val entities = CopyOnWriteArrayList<GameEntity>()
    var gravity: Float = 980f // بكسل لكل ثانية مربعة
    val camera = Camera2D()

    fun findEntityByName(name: String): GameEntity? {
        val clean = name.trim().lowercase()
        return entities.firstOrNull { it.name.trim().lowercase() == clean && it.isActive }
    }

    fun findEntitiesByTag(tag: String): List<GameEntity> {
        val clean = tag.trim().lowercase()
        return entities.filter { it.tag.trim().lowercase() == clean && it.isActive }
    }

    fun addEntity(entity: GameEntity) {
        entities.add(entity)
    }

    fun removeEntity(entity: GameEntity) {
        entities.remove(entity)
    }
}

/**
 * كاميرا ثنائية الأبعاد لملاحقة اللاعب واستعراض العالم
 */
class Camera2D {
    var position = Vector2(0f, 0f)
    var zoom: Float = 1.0f
    var targetEntity: GameEntity? = null
    var smoothSpeed: Float = 5.0f

    fun update(viewportWidth: Float, viewportHeight: Float, dt: Float) {
        targetEntity?.let { target ->
            val targetX = target.position.x + (target.width / 2f) - (viewportWidth / (2f * zoom))
            val targetY = target.position.y + (target.height / 2f) - (viewportHeight / (2f * zoom))
            position.x += (targetX - position.x) * smoothSpeed * dt
            position.y += (targetY - position.y) * smoothSpeed * dt
        }
    }
}

/**
 * مدير مدخلات اللمس وأزرار التحكم الافتراضية على الشاشة
 */
class InputManager {
    // حالة الأزرار اللمسية
    private val pressedButtons = ConcurrentHashMap<String, Boolean>()
    val joystickDir = Vector2(0f, 0f)
    var touchPosition = Vector2(0f, 0f)
    var isTouchDown: Boolean = false

    fun setButtonPressed(buttonKey: String, isDown: Boolean) {
        pressedButtons[buttonKey.trim().lowercase()] = isDown
    }

    fun isPressed(buttonKey: String): Boolean {
        val key = buttonKey.trim().lowercase()
        return pressedButtons[key] == true
    }

    fun clear() {
        pressedButtons.clear()
        joystickDir.set(0f, 0f)
        isTouchDown = false
    }
}

/**
 * مدير الصوتيات للمؤثرات والموسيقى
 */
class EngineAudioManager(context: Context) {
    private val soundPool: SoundPool
    private val soundMap = ConcurrentHashMap<String, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()
    }

    fun playSound(soundName: String) {
        // نغمات ومؤثرات افتراضية مدمجة
        soundMap[soundName]?.let { id ->
            soundPool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}

/**
 * محرك فيزياء خفيف وسريع لكشف التصادمات وحركة الجاذبية والقفز
 */
class PhysicsEngine {
    fun update(scene: GameScene, dt: Float, onCollision: (GameEntity, GameEntity) -> Unit) {
        val clampedDt = min(dt, 0.033f) // منع القفزات الكبيرة عند انخفاض الإطارات

        for (entity in scene.entities) {
            if (!entity.isActive) continue

            // تطبيق الجاذبية
            if (entity.affectedByGravity) {
                entity.velocity.y += scene.gravity * clampedDt
            }

            // حركة المحور الأفقي
            entity.position.x += entity.velocity.x * clampedDt
            checkCollisionsHorizontal(entity, scene, onCollision)

            // حركة المحور الرأسي
            entity.position.y += entity.velocity.y * clampedDt
            entity.isGrounded = false
            checkCollisionsVertical(entity, scene, onCollision)
        }
    }

    private fun checkCollisionsHorizontal(entity: GameEntity, scene: GameScene, onCollision: (GameEntity, GameEntity) -> Unit) {
        val box = entity.getBoundingBox()
        for (other in scene.entities) {
            if (other == entity || !other.isActive) continue
            val otherBox = other.getBoundingBox()

            if (RectF.intersects(box, otherBox)) {
                onCollision(entity, other)
                if (entity.isSolid && other.isSolid) {
                    if (entity.velocity.x > 0) {
                        entity.position.x = otherBox.left - entity.width * entity.scaleX
                    } else if (entity.velocity.x < 0) {
                        entity.position.x = otherBox.right
                    }
                    entity.velocity.x = 0f
                }
            }
        }
    }

    private fun checkCollisionsVertical(entity: GameEntity, scene: GameScene, onCollision: (GameEntity, GameEntity) -> Unit) {
        val box = entity.getBoundingBox()
        for (other in scene.entities) {
            if (other == entity || !other.isActive) continue
            val otherBox = other.getBoundingBox()

            if (RectF.intersects(box, otherBox)) {
                onCollision(entity, other)
                if (entity.isSolid && other.isSolid) {
                    if (entity.velocity.y > 0) {
                        // هبوط على سطح صلب
                        entity.position.y = otherBox.top - entity.height * entity.scaleY
                        entity.velocity.y = 0f
                        entity.isGrounded = true
                    } else if (entity.velocity.y < 0) {
                        // اصطدام بسقف
                        entity.position.y = otherBox.bottom
                        entity.velocity.y = 0f
                    }
                }
            }
        }
    }
}

/**
 * واجهة العرض السريعة (SurfaceView) وتشغيل حلقة اللعبة بمعدل 60 إطار في الثانية
 */
class MangoGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    var currentScene: GameScene = GameScene()
    val inputManager = InputManager()
    val physicsEngine = PhysicsEngine()
    val audioManager = EngineAudioManager(context)

    // مفسرات لغات البرمجة
    var onFrameUpdate: ((Float) -> Unit)? = null
    var onCollisionEvent: ((GameEntity, GameEntity) -> Unit)? = null

    // مؤشرات الأداء
    var fps: Int = 0
    var isRunning: Boolean = false
    var isPaused: Boolean = false
    var showDebugOverlay: Boolean = true

    private var gameThread: Thread? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        textSize = 34f
        typeface = Typeface.MONOSPACE
    }
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF5722")
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            gameThread = Thread(this, "MangoGameEngineLoop").apply { start() }
        }
    }

    fun stop() {
        isRunning = false
        try {
            gameThread?.join(500)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        gameThread = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stop()
    }

    override fun run() {
        var lastTime = System.nanoTime()
        var frameCount = 0
        var fpsTimer = System.currentTimeMillis()

        while (isRunning) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000_000f
            lastTime = now

            if (!isPaused) {
                // تحديث منطق اللعبة والسكريبتات
                onFrameUpdate?.invoke(dt)

                // تحديث الفيزياء والكاميرا
                physicsEngine.update(currentScene, dt) { a, b ->
                    onCollisionEvent?.invoke(a, b)
                }
                currentScene.camera.update(width.toFloat(), height.toFloat(), dt)
            }

            // رسم الإطار
            render()

            // قياس معدل الإطارات
            frameCount++
            if (System.currentTimeMillis() - fpsTimer >= 1000) {
                fps = frameCount
                frameCount = 0
                fpsTimer = System.currentTimeMillis()
            }

            // الموازنة للحفاظ على استهلاك البطارية وسلاسة 60fps
            try {
                Thread.sleep(14)
            } catch (e: Exception) {}
        }
    }

    private fun render() {
        val canvas = holder.lockCanvas() ?: return
        try {
            // مسح الخلفية
            canvas.drawColor(currentScene.backgroundColor)

            val cam = currentScene.camera
            canvas.save()
            canvas.scale(cam.zoom, cam.zoom)
            canvas.translate(-cam.position.x, -cam.position.y)

            // رسم كائنات المشهد
            for (entity in currentScene.entities) {
                if (!entity.isActive || !entity.isVisible) continue

                canvas.save()
                canvas.translate(entity.position.x, entity.position.y)
                canvas.rotate(entity.rotation, entity.width / 2f, entity.height / 2f)

                if (entity.spriteBitmap != null) {
                    val destRect = RectF(0f, 0f, entity.width * entity.scaleX, entity.height * entity.scaleY)
                    canvas.drawBitmap(entity.spriteBitmap!!, null, destRect, paint)
                } else {
                    paint.color = entity.color
                    canvas.drawRoundRect(
                        0f, 0f, entity.width * entity.scaleX, entity.height * entity.scaleY,
                        12f, 12f, paint
                    )
                }

                // مستطيل التصادم عند تفعيل وضع التصحيح
                if (showDebugOverlay && entity.isSolid) {
                    canvas.drawRect(0f, 0f, entity.width * entity.scaleX, entity.height * entity.scaleY, boxPaint)
                }

                // كتابة اسم الكائن فوقه
                if (showDebugOverlay) {
                    paint.color = Color.WHITE
                    paint.textSize = 22f
                    canvas.drawText(entity.name, 4f, -8f, paint)
                }

                canvas.restore()
            }

            canvas.restore()

            // رسم طبقة معلومات المحرك والـ FPS
            if (showDebugOverlay) {
                canvas.drawText("Mango 2D Engine • FPS: $fps", 30f, 60f, debugPaint)
                canvas.drawText("الكائنات: ${currentScene.entities.size}", 30f, 105f, debugPaint)
            }

        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
