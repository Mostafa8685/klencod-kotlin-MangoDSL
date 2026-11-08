package com.mangodsl.engine

import android.graphics.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * محرك تشغيل وتفسير نصوص بايثون للألعاب (Python Scripting Bridge)
 * يتيح للمطور كتابة كود بايثون قياسي للتحكم في كائنات اللعبة وفيزيائها وتفاعلاتها.
 */
class PythonDslEngine(
    private val scene: GameScene,
    private val input: InputManager,
    private val audio: EngineAudioManager,
    private val logCallback: ((String) -> Unit)? = null
) {

    // بيئة كائن بايثون الممررة للسكريبت
    class PyEntity(val entity: GameEntity, private val scene: GameScene) {
        val name: String get() = entity.name
        val tag: String get() = entity.tag
        var x: Float
            get() = entity.position.x
            set(v) { entity.position.x = v }
        var y: Float
            get() = entity.position.y
            set(v) { entity.position.y = v }
        var vx: Float
            get() = entity.velocity.x
            set(v) { entity.velocity.x = v }
        var vy: Float
            get() = entity.velocity.y
            set(v) { entity.velocity.y = v }
        var health: Float
            get() = entity.health
            set(v) { entity.health = v }
        var is_solid: Boolean
            get() = entity.isSolid
            set(v) { entity.isSolid = v }

        fun move(dx: Float, dy: Float) {
            entity.position.x += dx
            entity.position.y += dy
        }

        fun jump(power: Float = 350f) {
            if (entity.isGrounded || !entity.affectedByGravity) {
                entity.velocity.y = -power
                entity.isGrounded = false
            }
        }

        fun has_item(itemName: String): Boolean = entity.hasItem(itemName)
        fun add_item(itemName: String) = entity.addItem(itemName)
        fun remove_item(itemName: String): Boolean = entity.removeItem(itemName)
        fun destroy() {
            entity.isActive = false
            scene.removeEntity(entity)
        }
        fun set_color(hex: String) {
            try { entity.color = Color.parseColor(hex) } catch (e: Exception) {}
        }
    }

    class PyInput(private val input: InputManager) {
        fun is_pressed(key: String): Boolean = input.isPressed(key)
        fun get_axis_x(): Float = input.joystickDir.x
        fun get_axis_y(): Float = input.joystickDir.y
    }

    private val pyEntities = ConcurrentHashMap<String, PyEntity>()

    private fun getPyEntity(entity: GameEntity): PyEntity {
        return pyEntities.getOrPut(entity.id) {
            PyEntity(entity, scene)
        }
    }

    /**
     * تنفيذ دورة البداية بلغة بايثون
     */
    fun executeStart(entity: GameEntity) {
        val pyEntity = getPyEntity(entity)
        executePythonBlock(entity.scriptCode, "on_start", pyEntity, null, 0f)
    }

    /**
     * تنفيذ دورة الإطار (Update) بلغة بايثون
     */
    fun executeUpdate(entity: GameEntity, dt: Float) {
        val pyEntity = getPyEntity(entity)
        executePythonBlock(entity.scriptCode, "on_update", pyEntity, null, dt)
    }

    /**
     * تنفيذ دورة التصادم (Collision) بلغة بايثون
     */
    fun executeCollision(entity: GameEntity, other: GameEntity) {
        val pyEntity = getPyEntity(entity)
        val pyOther = getPyEntity(other)
        executePythonBlock(entity.scriptCode, "on_collision", pyEntity, pyOther, 0f)
    }

    /**
     * مفسر أوامر بايثون السريع والمباشر
     */
    private fun executePythonBlock(code: String, targetFunc: String, self: PyEntity, other: PyEntity?, dt: Float) {
        val lines = code.lines()
        var insideFunc = false
        var funcIndent = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            if (trimmed.startsWith("def ")) {
                val funcName = trimmed.removePrefix("def ").substringBefore("(").trim()
                if (funcName == targetFunc) {
                    insideFunc = true
                    funcIndent = rawLine.takeWhile { it.isWhitespace() }.length
                    continue
                } else {
                    insideFunc = false
                }
            }

            if (insideFunc) {
                val currentIndent = rawLine.takeWhile { it.isWhitespace() }.length
                if (currentIndent <= funcIndent && trimmed.isNotEmpty()) {
                    // خروج من كتلة الدالة
                    insideFunc = false
                    continue
                }
                executePythonLine(trimmed, self, other, dt)
            }
        }
    }

    private fun executePythonLine(line: String, self: PyEntity, other: PyEntity?, dt: Float) {
        // فحص مدخلات لوحة التحكم والأزرار
        if (line.startsWith("if input.is_pressed(") || line.startsWith("if input.is_key_down(")) {
            val key = extractArg(line)
            val action = line.substringAfter(":", "").trim()
            if (input.isPressed(key) && action.isNotEmpty()) {
                executeAction(action, self, other)
            }
            return
        }

        // فحص توفر عنصر
        if (line.startsWith("if entity.has_item(") || line.startsWith("if self.has_item(")) {
            val item = extractArg(line)
            val action = line.substringAfter(":", "").trim()
            if (self.has_item(item) && action.isNotEmpty()) {
                executeAction(action, self, other)
            }
            return
        }

        // تنفيذ أوامر مباشرة
        executeAction(line, self, other)
    }

    private fun executeAction(action: String, self: PyEntity, other: PyEntity?) {
        when {
            action.startsWith("self.jump(") || action.startsWith("entity.jump(") -> {
                val power = extractArg(action).toFloatOrNull() ?: 350f
                self.jump(power)
            }
            action.startsWith("self.vx =") || action.startsWith("entity.vx =") -> {
                val v = action.substringAfter("=").trim().toFloatOrNull() ?: 0f
                self.vx = v
            }
            action.startsWith("self.vy =") || action.startsWith("entity.vy =") -> {
                val v = action.substringAfter("=").trim().toFloatOrNull() ?: 0f
                self.vy = v
            }
            action.startsWith("self.add_item(") || action.startsWith("entity.add_item(") -> {
                val item = extractArg(action)
                self.add_item(item)
                logCallback?.invoke("Python [Item Added]: $item")
            }
            action.startsWith("self.remove_item(") || action.startsWith("entity.remove_item(") -> {
                val item = extractArg(action)
                self.remove_item(item)
                logCallback?.invoke("Python [Item Removed]: $item")
            }
            action.startsWith("self.destroy()") || action.startsWith("entity.destroy()") -> {
                self.destroy()
            }
            action.startsWith("other.destroy()") -> {
                other?.destroy()
            }
            action.startsWith("print(") || action.startsWith("log(") -> {
                val msg = extractArg(action)
                logCallback?.invoke("Python: $msg")
            }
            action.startsWith("audio.play(") || action.startsWith("sound.play(") -> {
                val sound = extractArg(action)
                audio.playSound(sound)
            }
        }
    }

    private fun extractArg(str: String): String {
        val start = str.indexOf("(")
        val end = str.lastIndexOf(")")
        if (start != -1 && end > start) {
            val content = str.substring(start + 1, end).trim()
            return content.replace("\"", "").replace("'", "")
        }
        return ""
    }
}
