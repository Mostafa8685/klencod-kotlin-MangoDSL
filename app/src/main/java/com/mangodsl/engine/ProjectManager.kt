package com.mangodsl.engine

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * مدير المشاريع والألعاب في Mango DSL
 * يتولى حفظ واسترجاع المشاهد وتحميل الألعاب النموذجية المدمجة.
 */
object ProjectManager {

    /**
     * إنشاء لعبة "لغز الحجر السحري" الافتراضية لعرض قدرات لغة الضاد
     */
    fun createDefaultMagicStoneGame(): GameScene {
        val scene = GameScene("مغامرة_الحجر_السحري")
        scene.backgroundColor = Color.parseColor("#141824")
        scene.gravity = 920f

        // 1. كائن اللاعب
        val player = GameEntity(name = "اللاعب", tag = "بطل").apply {
            position.set(100f, 300f)
            width = 54f
            height = 70f
            color = Color.parseColor("#00E676") // أخضر مشرق
            isSolid = true
            affectedByGravity = true
            scriptType = ScriptType.ARABIC_DSL
            scriptCode = """عند_البدء {
    السرعة = 200
    الصحة = 100
}

في_كل_إطار {
    إذا ضغط اللاعب "يمين" {
        تحرك في اتجاه يمين بسرعة السرعة
    }
    إذا ضغط اللاعب "يسار" {
        تحرك في اتجاه يسار بسرعة السرعة
    }
    إذا ضغط اللاعب "s" {
        اقفز بقوة 420
        شغل صوت "قفزة_البطل"
    }
}"""
        }
        scene.addEntity(player)

        // 2. كائن الأرضية الصلبة
        val ground = GameEntity(name = "أرضية_المرحلة", tag = "جدار").apply {
            position.set(0f, 520f)
            width = 1200f
            height = 80f
            color = Color.parseColor("#37474F")
            isSolid = true
            affectedByGravity = false
            scriptType = ScriptType.NONE
        }
        scene.addEntity(ground)

        // 3. منصة مرتفعة
        val platform = GameEntity(name = "منصة_حجرية", tag = "جدار").apply {
            position.set(280f, 380f)
            width = 180f
            height = 36f
            color = Color.parseColor("#546E7A")
            isSolid = true
            affectedByGravity = false
            scriptType = ScriptType.NONE
        }
        scene.addEntity(platform)

        // 4. كائن الحجر السحري (عنصر قابل للجمع)
        val magicStone = GameEntity(name = "حجر_سحري", tag = "عنصر").apply {
            position.set(340f, 320f)
            width = 44f
            height = 44f
            color = Color.parseColor("#E040FB") // بنفسجي متوهج
            isSolid = false
            affectedByGravity = false
            scriptType = ScriptType.ARABIC_DSL
            scriptCode = """عند_التصادم مع اللاعب {
    أضف عنصر "حجر_سحري" إلى المخزون
    شغل صوت "التقاط_الحجر"
    أظهر رسالة "حصلت على الحجر السحري العظيم!"
    دمر_الكائن هذا_الكائن
}"""
        }
        scene.addEntity(magicStone)

        // 5. كائن الباب الحجري المغلق
        val stoneDoor = GameEntity(name = "الباب_الحجري", tag = "بوابة").apply {
            position.set(650f, 400f)
            width = 60f
            height = 120f
            color = Color.parseColor("#8D6E63") // بني حجري مغلق
            isSolid = true
            affectedByGravity = false
            scriptType = ScriptType.ARABIC_DSL
            scriptCode = """عند_التصادم مع اللاعب {
    إذا توفر عنصر "حجر_سحري" في المخزون {
        افتح الباب_الحجري
        احذف عنصر "حجر_سحري" من المخزون
        شغل صوت "فتح_الباب"
        غير المظهر إلى "#81C784"
        عطّل التصادم مع هذا_الكائن
        أظهر رسالة "فُتحت البوابة ببركة الحجر السحري! مبروك!"
    } وإلا {
        أظهر رسالة "تحتاج إلى حجر سحري لفتح هذا الباب الحجري!"
    }
}"""
        }
        scene.addEntity(stoneDoor)

        // جعل الكاميرا تلاحق اللاعب
        scene.camera.targetEntity = player

        return scene
    }

    /**
     * إنشاء لعبة نموذجية بلغة بايثون
     */
    fun createPythonDemoGame(): GameScene {
        val scene = GameScene("سباق_الفضاء_بايثون")
        scene.backgroundColor = Color.parseColor("#0B0C10")
        scene.gravity = 0f // فضاء بدون جاذبية

        val ship = GameEntity(name = "مركبة_الفضاء", tag = "لاعب").apply {
            position.set(200f, 300f)
            width = 60f
            height = 60f
            color = Color.parseColor("#29B6F6")
            isSolid = true
            scriptType = ScriptType.PYTHON
            scriptCode = """def on_start(entity):
    print("Ship online!")
    entity.health = 100

def on_update(entity, dt):
    if input.is_pressed("يمين"):
        entity.vx = 220
    if input.is_pressed("يسار"):
        entity.vx = -220
    if input.is_pressed("أعلى"):
        entity.vy = -220
    if input.is_pressed("أسفل"):
        entity.vy = 220
    if input.is_pressed("s"):
        print("Hyper Speed Boost!")
        entity.vx = 400
"""
        }
        scene.addEntity(ship)

        val crystal = GameEntity(name = "بلورة_الطاقة", tag = "عنصر").apply {
            position.set(500f, 300f)
            width = 40f
            height = 40f
            color = Color.parseColor("#FFD54F")
            isSolid = false
            scriptType = ScriptType.PYTHON
            scriptCode = """def on_collision(entity, other):
    if other.tag == "لاعب":
        other.add_item("بلورة")
        print("Crystal Collected!")
        self.destroy()
"""
        }
        scene.addEntity(crystal)

        scene.camera.targetEntity = ship
        return scene
    }

    /**
     * حفظ المشهد كملف JSON
     */
    fun saveSceneToJson(context: Context, scene: GameScene, fileName: String = "game_scene.json"): Boolean {
        return try {
            val root = JSONObject()
            root.put("sceneName", scene.name)
            root.put("backgroundColor", scene.backgroundColor)
            root.put("gravity", scene.gravity.toDouble())

            val entitiesArray = JSONArray()
            for (e in scene.entities) {
                val obj = JSONObject()
                obj.put("id", e.id)
                obj.put("name", e.name)
                obj.put("tag", e.tag)
                obj.put("x", e.position.x.toDouble())
                obj.put("y", e.position.y.toDouble())
                obj.put("width", e.width.toDouble())
                obj.put("height", e.height.toDouble())
                obj.put("color", e.color)
                obj.put("isSolid", e.isSolid)
                obj.put("affectedByGravity", e.affectedByGravity)
                obj.put("scriptType", e.scriptType.name)
                obj.put("scriptCode", e.scriptCode)
                entitiesArray.put(obj)
            }
            root.put("entities", entitiesArray)

            val file = File(context.filesDir, fileName)
            file.writeText(root.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
