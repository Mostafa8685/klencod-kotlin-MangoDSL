package com.mangodsl.engine

import android.graphics.Color
import android.util.Log

/**
 * مفسر لغة البرمجة العربية لمحرك ألعاب Mango DSL
 * يحلل النصوص البرمجية العربية ويحولها إلى شجرة أوامر (AST) تنفذ مباشرة داخل حلقة اللعبة.
 */
class ArabicDslEngine(
    private val scene: GameScene,
    private val input: InputManager,
    private val audio: EngineAudioManager,
    private val logCallback: ((String) -> Unit)? = null
) {

    // شجرة العقد والأوامر
    sealed class Statement {
        data class MoveDirection(val direction: String, val speedExpr: String) : Statement()
        data class MoveTowards(val targetName: String, val speedExpr: String) : Statement()
        data class Jump(val powerExpr: String) : Statement()
        data class IfKeyPressed(val key: String, val thenBranch: List<Statement>) : Statement()
        data class IfHasItem(val itemName: String, val thenBranch: List<Statement>, val elseBranch: List<Statement>) : Statement()
        data class IfCondition(val leftExpr: String, val op: String, val rightExpr: String, val thenBranch: List<Statement>, val elseBranch: List<Statement>) : Statement()
        data class AddItem(val itemName: String) : Statement()
        data class RemoveItem(val itemName: String) : Statement()
        data class OpenEntity(val targetName: String) : Statement()
        data class CloseEntity(val targetName: String) : Statement()
        data class DestroyEntity(val targetName: String) : Statement()
        data class ChangeSprite(val spriteOrColor: String) : Statement()
        data class ChangePosition(val xExpr: String, val yExpr: String) : Statement()
        data class PlaySound(val soundName: String) : Statement()
        data class ShowMessage(val message: String) : Statement()
        data class SetVariable(val varName: String, val expr: String) : Statement()
        object DisableCollision : Statement()
    }

    data class CompiledScript(
        val onStart: MutableList<Statement> = mutableListOf(),
        val onUpdate: MutableList<Statement> = mutableListOf(),
        val onCollision: MutableList<Pair<String, List<Statement>>> = mutableListOf()
    )

    private val scriptCache = mutableMapOf<String, CompiledScript>()

    /**
     * ترجمة وتحليل كود الكائن وتخزينه في الذاكرة
     */
    fun compile(code: String): CompiledScript {
        val compiled = CompiledScript()
        val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (line.startsWith("عند_البدء")) {
                val block = extractBlock(lines, i)
                compiled.onStart.addAll(parseStatements(block.first))
                i = block.second
            } else if (line.startsWith("في_كل_إطار")) {
                val block = extractBlock(lines, i)
                compiled.onUpdate.addAll(parseStatements(block.first))
                i = block.second
            } else if (line.startsWith("عند_التصادم مع") || line.startsWith("عند_التصادم")) {
                val target = line.removePrefix("عند_التصادم مع").removePrefix("عند_التصادم").replace("{", "").trim()
                val block = extractBlock(lines, i)
                compiled.onCollision.add(Pair(target, parseStatements(block.first)))
                i = block.second
            } else {
                // عبارات عامة خارج الكتل تُدرج في التحديث
                val stmts = parseSingleLine(line)
                if (stmts != null) compiled.onUpdate.add(stmts)
                i++
            }
        }
        return compiled
    }

    private fun extractBlock(lines: List<String>, startIndex: Int): Pair<List<String>, Int> {
        val block = mutableListOf<String>()
        var depth = 0
        var i = startIndex
        var foundOpen = false

        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("{")) {
                depth += countOccurrences(line, '{')
                foundOpen = true
            }

            if (i > startIndex) {
                if (line.contains("}")) {
                    depth -= countOccurrences(line, '}')
                    val cleanLine = line.replace("}", "").trim()
                    if (cleanLine.isNotEmpty() && depth >= 0) {
                        block.add(cleanLine)
                    }
                } else {
                    block.add(line)
                }
            }

            if (foundOpen && depth <= 0) {
                break
            }
            i++
        }
        return Pair(block, i + 1)
    }

    private fun countOccurrences(str: String, ch: Char): Int = str.count { it == ch }

    private fun parseStatements(lines: List<String>): List<Statement> {
        val result = mutableListOf<Statement>()
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx].trim()
            if (line.isEmpty() || line.startsWith("#")) {
                idx++
                continue
            }

            // فحص الشروط المتفرعة: إذا ضغط اللاعب
            if (line.startsWith("إذا ضغط اللاعب") || line.startsWith("اذا ضغط الاعب") || line.startsWith("إذا استمر الضغط على")) {
                val key = extractQuotesOrWord(line.removePrefix("إذا ضغط اللاعب").removePrefix("اذا ضغط الاعب").removePrefix("إذا استمر الضغط على"))
                val block = extractBlock(lines, idx)
                result.add(Statement.IfKeyPressed(key, parseStatements(block.first)))
                idx = block.second
                continue
            }

            // فحص توفر العناصر: إذا توفر عنصر
            if (line.startsWith("إذا توفر عنصر") || line.startsWith("اذا توفر عنصر")) {
                val after = line.removePrefix("إذا توفر عنصر").removePrefix("اذا توفر عنصر")
                val itemName = extractQuotesOrWord(after.substringBefore("في المخزون"))
                val block = extractBlock(lines, idx)

                var elseStatements = emptyList<Statement>()
                var nextIdx = block.second
                if (nextIdx < lines.size && lines[nextIdx].contains("وإلا")) {
                    val elseBlock = extractBlock(lines, nextIdx)
                    elseStatements = parseStatements(elseBlock.first)
                    nextIdx = elseBlock.second
                }

                result.add(Statement.IfHasItem(itemName, parseStatements(block.first), elseStatements))
                idx = nextIdx
                continue
            }

            // سطر أوامر مفرد
            val stmt = parseSingleLine(line)
            if (stmt != null) {
                result.add(stmt)
            }
            idx++
        }
        return result
    }

    private fun parseSingleLine(line: String): Statement? {
        val clean = line.replace(";", "").trim()

        // تحرك في اتجاه
        if (clean.startsWith("تحرك في اتجاه") || clean.startsWith("تحرك في اتجاة")) {
            val parts = clean.removePrefix("تحرك في اتجاه").removePrefix("تحرك في اتجاة").trim()
            val dir = parts.substringBefore("بسرعة").trim()
            val speed = parts.substringAfter("بسرعة", "150").trim()
            return Statement.MoveDirection(dir, speed)
        }

        // تحرك نحو كائن
        if (clean.startsWith("تحرك نحو كائن") || clean.startsWith("تحرك نحو")) {
            val parts = clean.removePrefix("تحرك نحو كائن").removePrefix("تحرك نحو").trim()
            val target = parts.substringBefore("بسرعة").trim()
            val speed = parts.substringAfter("بسرعة", "100").trim()
            return Statement.MoveTowards(target, speed)
        }

        // اقفز بقوة
        if (clean.startsWith("اقفز بقوة") || clean.startsWith("اقفز")) {
            val power = clean.removePrefix("اقفز بقوة").removePrefix("اقفز").trim()
            return Statement.Jump(if (power.isEmpty()) "350" else power)
        }

        // أضف عنصر
        if (clean.startsWith("أضف عنصر") || clean.startsWith("اضف عنصر")) {
            val item = extractQuotesOrWord(clean.removePrefix("أضف عنصر").removePrefix("اضف عنصر").substringBefore("إلى المخزون"))
            return Statement.AddItem(item)
        }

        // احذف عنصر
        if (clean.startsWith("احذف عنصر")) {
            val item = extractQuotesOrWord(clean.removePrefix("احذف عنصر").substringBefore("من المخزون"))
            return Statement.RemoveItem(item)
        }

        // افتح / أغلق
        if (clean.startsWith("افتح")) {
            val target = clean.removePrefix("افتح").trim()
            return Statement.OpenEntity(target)
        }
        if (clean.startsWith("أغلق") || clean.startsWith("اغلق")) {
            val target = clean.removePrefix("أغلق").removePrefix("اغلق").trim()
            return Statement.CloseEntity(target)
        }

        // دمر_الكائن
        if (clean.startsWith("دمر_الكائن") || clean.startsWith("دمر")) {
            val target = clean.removePrefix("دمر_الكائن").removePrefix("دمر").trim()
            return Statement.DestroyEntity(target)
        }

        // غير المظهر إلى
        if (clean.startsWith("غير المظهر إلى") || clean.startsWith("غير_المظهر إلى")) {
            val res = extractQuotesOrWord(clean.removePrefix("غير المظهر إلى").removePrefix("غير_المظهر إلى"))
            return Statement.ChangeSprite(res)
        }

        // شغل صوت
        if (clean.startsWith("شغل صوت") || clean.startsWith("شغل_صوت")) {
            val sound = extractQuotesOrWord(clean.removePrefix("شغل صوت").removePrefix("شغل_صوت"))
            return Statement.PlaySound(sound)
        }

        // أظهر رسالة
        if (clean.startsWith("أظهر رسالة") || clean.startsWith("اظهر رسالة")) {
            val msg = extractQuotesOrWord(clean.removePrefix("أظهر رسالة").removePrefix("اظهر رسالة"))
            return Statement.ShowMessage(msg)
        }

        // تعطيل التصادم
        if (clean.contains("عطّل التصادم") || clean.contains("عطل التصادم")) {
            return Statement.DisableCollision
        }

        // إسناد المتغيرات: اجعل س = ص أو س = ص
        if (clean.startsWith("اجعل")) {
            val assignment = clean.removePrefix("اجعل").trim()
            val varName = assignment.substringBefore("=").trim()
            val expr = assignment.substringAfter("=").trim()
            return Statement.SetVariable(varName, expr)
        } else if (clean.contains("=")) {
            val varName = clean.substringBefore("=").trim()
            val expr = clean.substringAfter("=").trim()
            return Statement.SetVariable(varName, expr)
        }

        return null
    }

    private fun extractQuotesOrWord(text: String): String {
        var str = text.trim()
        if (str.contains("\"")) {
            val start = str.indexOf("\"")
            val end = str.indexOf("\"", start + 1)
            if (start != -1 && end != -1) {
                return str.substring(start + 1, end).trim()
            }
        }
        return str.replace("{", "").replace("}", "").trim()
    }

    /**
     * تنفيذ دورة البداية للكائن
     */
    fun executeStart(entity: GameEntity) {
        val script = getOrCompile(entity)
        executeStatements(entity, script.onStart, null)
    }

    /**
     * تنفيذ دورة الإطار (Update) في كل لحظة
     */
    fun executeUpdate(entity: GameEntity, dt: Float) {
        val script = getOrCompile(entity)
        executeStatements(entity, script.onUpdate, null)
    }

    /**
     * تنفيذ عند حدوث تصادم مع كائن آخر
     */
    fun executeCollision(entity: GameEntity, other: GameEntity) {
        val script = getOrCompile(entity)
        for ((target, stmts) in script.onCollision) {
            if (target.isEmpty() || target == "الكل" || target == other.name || target == other.tag) {
                executeStatements(entity, stmts, other)
            }
        }
    }

    private fun getOrCompile(entity: GameEntity): CompiledScript {
        return scriptCache.getOrPut(entity.id + "_" + entity.scriptCode.hashCode()) {
            compile(entity.scriptCode)
        }
    }

    private fun executeStatements(entity: GameEntity, statements: List<Statement>, other: GameEntity?) {
        for (stmt in statements) {
            when (stmt) {
                is Statement.MoveDirection -> {
                    val speed = evalFloat(stmt.speedExpr, entity, 150f)
                    when (stmt.direction.trim()) {
                        "يمين", "شرق", "right" -> entity.velocity.x = speed
                        "يسار", "غرب", "left" -> entity.velocity.x = -speed
                        "أعلى", "اعلى", "شمال", "up" -> entity.velocity.y = -speed
                        "أسفل", "اسفل", "جنوب", "down" -> entity.velocity.y = speed
                        "توقف", "ثابت" -> entity.velocity.x = 0f
                    }
                }
                is Statement.MoveTowards -> {
                    val speed = evalFloat(stmt.speedExpr, entity, 100f)
                    val target = scene.findEntityByName(stmt.targetName)
                    if (target != null) {
                        val dx = target.position.x - entity.position.x
                        val dy = target.position.y - entity.position.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist > 1f) {
                            entity.velocity.x = (dx / dist) * speed
                            entity.velocity.y = (dy / dist) * speed
                        }
                    }
                }
                is Statement.Jump -> {
                    val power = evalFloat(stmt.powerExpr, entity, 350f)
                    // القفز عندما يكون ملامساً للأرض أو متاحاً
                    if (entity.isGrounded || !entity.affectedByGravity) {
                        entity.velocity.y = -power
                        entity.isGrounded = false
                    }
                }
                is Statement.IfKeyPressed -> {
                    if (input.isPressed(stmt.key)) {
                        executeStatements(entity, stmt.thenBranch, other)
                    }
                }
                is Statement.IfHasItem -> {
                    if (entity.hasItem(stmt.itemName)) {
                        executeStatements(entity, stmt.thenBranch, other)
                    } else {
                        executeStatements(entity, stmt.elseBranch, other)
                    }
                }
                is Statement.IfCondition -> {
                    val left = evalFloat(stmt.leftExpr, entity, 0f)
                    val right = evalFloat(stmt.rightExpr, entity, 0f)
                    val conditionMet = when (stmt.op) {
                        "==" -> left == right
                        "!=" -> left != right
                        ">" -> left > right
                        "<" -> left < right
                        ">=" -> left >= right
                        "<=" -> left <= right
                        else -> false
                    }
                    if (conditionMet) {
                        executeStatements(entity, stmt.thenBranch, other)
                    } else {
                        executeStatements(entity, stmt.elseBranch, other)
                    }
                }
                is Statement.AddItem -> {
                    entity.addItem(stmt.itemName)
                    logCallback?.invoke("أضيف إلى المخزون: ${stmt.itemName}")
                }
                is Statement.RemoveItem -> {
                    entity.removeItem(stmt.itemName)
                    logCallback?.invoke("حذف من المخزون: ${stmt.itemName}")
                }
                is Statement.OpenEntity -> {
                    val target = if (stmt.targetName == "هذا_الكائن" || stmt.targetName.isEmpty()) entity else scene.findEntityByName(stmt.targetName)
                    target?.let {
                        it.attributes["حالة"] = "مفتوح"
                        it.isSolid = false
                        it.color = Color.parseColor("#4CAF50") // مظهر الفتح
                        logCallback?.invoke("تم فتح الكائن: ${it.name}")
                    }
                }
                is Statement.CloseEntity -> {
                    val target = if (stmt.targetName == "هذا_الكائن" || stmt.targetName.isEmpty()) entity else scene.findEntityByName(stmt.targetName)
                    target?.let {
                        it.attributes["حالة"] = "مغلق"
                        it.isSolid = true
                        it.color = Color.parseColor("#B0BEC5")
                    }
                }
                is Statement.DestroyEntity -> {
                    val target = if (stmt.targetName == "هذا_الكائن" || stmt.targetName.isEmpty()) entity else scene.findEntityByName(stmt.targetName)
                    target?.let {
                        it.isActive = false
                        scene.removeEntity(it)
                        logCallback?.invoke("تم تدمير: ${it.name}")
                    }
                }
                is Statement.ChangeSprite -> {
                    val visual = stmt.spriteOrColor.trim()
                    if (visual.startsWith("#")) {
                        try { entity.color = Color.parseColor(visual) } catch (e: Exception) {}
                    } else {
                        entity.spriteName = visual
                        // تلوين ذكي حسب الاسم إذا لم توجد صورة
                        when {
                            visual.contains("مفتوح") -> entity.color = Color.parseColor("#81C784")
                            visual.contains("حجر") -> entity.color = Color.parseColor("#BA68C8")
                            visual.contains("ذهب") || visual.contains("عملة") -> entity.color = Color.parseColor("#FFD54F")
                            visual.contains("باب") -> entity.color = Color.parseColor("#8D6E63")
                        }
                    }
                }
                is Statement.ChangePosition -> {
                    entity.position.x = evalFloat(stmt.xExpr, entity, entity.position.x)
                    entity.position.y = evalFloat(stmt.yExpr, entity, entity.position.y)
                }
                is Statement.PlaySound -> {
                    audio.playSound(stmt.soundName)
                    logCallback?.invoke("تشغيل صوت: ${stmt.soundName}")
                }
                is Statement.ShowMessage -> {
                    logCallback?.invoke(stmt.message)
                }
                is Statement.SetVariable -> {
                    val value = evalFloat(stmt.expr, entity, 0f)
                    entity.attributes[stmt.varName] = value
                }
                is Statement.DisableCollision -> {
                    entity.isSolid = false
                }
            }
        }
    }

    private fun evalFloat(expr: String, entity: GameEntity, defaultVal: Float): Float {
        val trimmed = expr.trim()
        trimmed.toFloatOrNull()?.let { return it }

        // فحص المتغيرات الخاصة بالكائن
        when (trimmed) {
            "السرعة", "سرعة" -> return (entity.attributes["السرعة"] as? Float) ?: 180f
            "الصحة", "صحة" -> return entity.health
            "الموضع_س", "س" -> return entity.position.x
            "الموضع_ص", "ص" -> return entity.position.y
        }

        if (entity.attributes.containsKey(trimmed)) {
            val v = entity.attributes[trimmed]
            if (v is Number) return v.toFloat()
        }

        return defaultVal
    }
}
