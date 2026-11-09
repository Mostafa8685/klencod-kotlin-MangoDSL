package com.mangodsl.engine

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * الواجهة الرئيسية لمحرك Mango DSL 2D على أندرويد
 * تضم: المشغل الحي، محرر المشاهد، استوديو الأكواد (عربي وبايثون)، مدير الأصول، وقاموس لغة الضاد.
 */
class MainActivity : Activity() {

    private lateinit var gameView: MangoGameView
    private lateinit var arabicEngine: ArabicDslEngine
    private lateinit var pythonEngine: PythonDslEngine

    private var activeTab = 0 // 0: اللعب، 1: المشهد، 2: الأكواد، 3: الأصول، 4: القاموس
    private lateinit var contentContainer: FrameLayout
    private lateinit var gameRunnerLayout: FrameLayout
    private lateinit var sceneEditorLayout: LinearLayout
    private lateinit var codeEditorLayout: LinearLayout
    private lateinit var assetManagerLayout: LinearLayout
    private lateinit var dictionaryLayout: LinearLayout

    private lateinit var statusBanner: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    // كائن محرر الكود الحالي
    private var selectedEntity: GameEntity? = null
    private lateinit var codeEditText: EditText
    private lateinit var scriptTypeSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إعداد واجهة برمجية كاملة وأنيقة
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12131A"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. شريط العنوان العلوي (Top Header Bar)
        val headerBar = createHeaderBar()
        rootLayout.addView(headerBar)

        // 2. شريط التبويبات الرئيسي (Navigation Tabs)
        val navTabsBar = createNavigationTabs()
        rootLayout.addView(navTabsBar)

        // 3. شريط التنبيهات ورسائل اللعبة الحية
        statusBanner = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#1E202B"))
            setTextColor(Color.parseColor("#00E676"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(12, 12, 12, 12)
            visibility = View.GONE
        }
        rootLayout.addView(statusBanner)

        // 4. الحاوية المركزية للتبويبات
        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        rootLayout.addView(contentContainer)

        // تهيئة محرك اللعبة
        initEngine()

        // بناء الشاشات الخمس
        setupGameRunner()
        setupSceneEditor()
        setupCodeEditor()
        setupAssetManager()
        setupDictionary()

        // العرض المبدئي لمشغل اللعبة
        switchTab(0)

        setContentView(rootLayout)
    }

    private fun initEngine() {
        gameView = MangoGameView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // تحميل لعبة المغامرة والحجر السحري الافتراضية
        val defaultScene = ProjectManager.createDefaultMagicStoneGame()
        gameView.currentScene = defaultScene
        selectedEntity = defaultScene.findEntityByName("اللاعب")

        arabicEngine = ArabicDslEngine(defaultScene, gameView.inputManager, gameView.audioManager) { msg ->
            showGameMessage(msg)
        }

        pythonEngine = PythonDslEngine(defaultScene, gameView.inputManager, gameView.audioManager) { msg ->
            showGameMessage(msg)
        }

        // ربط تحديث الإطارات
        gameView.onFrameUpdate = { dt ->
            for (entity in gameView.currentScene.entities) {
                if (!entity.isActive) continue
                when (entity.scriptType) {
                    ScriptType.ARABIC_DSL -> arabicEngine.executeUpdate(entity, dt)
                    ScriptType.PYTHON -> pythonEngine.executeUpdate(entity, dt)
                    ScriptType.NONE -> {}
                }
            }
        }

        // ربط أحداث التصادم
        gameView.onCollisionEvent = { a, b ->
            if (a.scriptType == ScriptType.ARABIC_DSL) arabicEngine.executeCollision(a, b)
            if (b.scriptType == ScriptType.ARABIC_DSL) arabicEngine.executeCollision(b, a)
            if (a.scriptType == ScriptType.PYTHON) pythonEngine.executeCollision(a, b)
            if (b.scriptType == ScriptType.PYTHON) pythonEngine.executeCollision(b, a)
        }

        // تشغيل دورة البداية للكائنات
        for (entity in defaultScene.entities) {
            if (entity.scriptType == ScriptType.ARABIC_DSL) arabicEngine.executeStart(entity)
            if (entity.scriptType == ScriptType.PYTHON) pythonEngine.executeStart(entity)
        }
    }

    private fun showGameMessage(msg: String) {
        mainHandler.post {
            statusBanner.text = "📢 $msg"
            statusBanner.visibility = View.VISIBLE
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.postDelayed({
                statusBanner.visibility = View.GONE
            }, 3000)
        }
    }

    private fun createHeaderBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#181A22"))

            // أيقونة وعنوان المحرك
            val titleText = TextView(context).apply {
                text = "🥭 Mango DSL 2D"
                textSize = 17f
                setTextColor(Color.parseColor("#FF7043"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleText)

            // أزرار التحكم في التشغيل
            val playBtn = createStyledButton("▶ تشغيل", "#00E676", "#000000") {
                gameView.isPaused = false
                showGameMessage("تم استئناف اللعبة")
            }
            val pauseBtn = createStyledButton("⏸ إيقاف", "#FFA726", "#000000") {
                gameView.isPaused = true
                showGameMessage("اللعبة متوقفة مؤقتاً")
            }
            val resetBtn = createStyledButton("⟲ إعادة", "#EF5350", "#FFFFFF") {
                resetCurrentGame()
            }

            addView(playBtn)
            addView(createSpacer(12))
            addView(pauseBtn)
            addView(createSpacer(12))
            addView(resetBtn)
        }
    }

    private fun resetCurrentGame() {
        val newScene = ProjectManager.createDefaultMagicStoneGame()
        gameView.currentScene = newScene
        selectedEntity = newScene.findEntityByName("اللاعب")
        initEngine()
        showGameMessage("تمت إعادة ضبط اللعبة بنجاح")
    }

    private fun createNavigationTabs(): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#1E202B"))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 10, 12, 10)
        }

        val tabs = listOf(
            "🎮 اللعب والتشغيل" to 0,
            "🎨 محرر المشهد" to 1,
            "📝 محرر الأكواد" to 2,
            "📁 مدير الأصول" to 3,
            "📖 قاموس الضاد" to 4
        )

        for ((title, index) in tabs) {
            val tabBtn = TextView(this).apply {
                text = title
                textSize = 13.5f
                setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#94A3B8"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(28, 16, 28, 16)
                background = createRoundedDrawable(if (index == 0) "#FF7043" else "#272A38", 20f)
                setOnClickListener { switchTab(index) }
            }
            row.addView(tabBtn)
            row.addView(createSpacer(10))
        }

        scroll.addView(row)
        return scroll
    }

    private fun switchTab(tabIndex: Int) {
        activeTab = tabIndex
        contentContainer.removeAllViews()

        when (tabIndex) {
            0 -> contentContainer.addView(gameRunnerLayout)
            1 -> {
                refreshSceneEditorUI()
                contentContainer.addView(sceneEditorLayout)
            }
            2 -> {
                refreshCodeEditorUI()
                contentContainer.addView(codeEditorLayout)
            }
            3 -> contentContainer.addView(assetManagerLayout)
            4 -> contentContainer.addView(dictionaryLayout)
        }
    }

    // ==========================================
    // 1. شاشة اللعب والتحكم الافتراضي (Game Runner)
    // ==========================================
    private fun setupGameRunner() {
        gameRunnerLayout = FrameLayout(this)
        gameRunnerLayout.addView(gameView)

        // لوحة التحكم اللمسية الافتراضية فوق اللعبة
        val controlsOverlay = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // أزرار الاتجاهات (يسار الشاشة)
        val dpadLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                setMargins(32, 0, 0, 48)
            }
        }

        val leftBtn = createVirtualButton("◀", "يسار")
        val rightBtn = createVirtualButton("▶", "يمين")
        val upBtn = createVirtualButton("▲", "أعلى")
        val downBtn = createVirtualButton("▼", "أسفل")

        dpadLayout.addView(leftBtn)
        dpadLayout.addView(createSpacer(14))
        dpadLayout.addView(rightBtn)
        dpadLayout.addView(createSpacer(14))
        dpadLayout.addView(upBtn)
        dpadLayout.addView(createSpacer(14))
        dpadLayout.addView(downBtn)

        controlsOverlay.addView(dpadLayout)

        // أزرار الأكشن (يمين الشاشة)
        val actionButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                setMargins(0, 0, 32, 48)
            }
        }

        val jumpBtn = createVirtualButton("S\nقفز", "s", "#00E676")
        val actionABtn = createVirtualButton("A\nتفاعل", "a", "#FFA726")
        val actionBBtn = createVirtualButton("B\nهجوم", "b", "#E040FB")

        actionButtonsLayout.addView(actionBBtn)
        actionButtonsLayout.addView(createSpacer(14))
        actionButtonsLayout.addView(actionABtn)
        actionButtonsLayout.addView(createSpacer(14))
        actionButtonsLayout.addView(jumpBtn)

        controlsOverlay.addView(actionButtonsLayout)
        gameRunnerLayout.addView(controlsOverlay)
    }

    private fun createVirtualButton(label: String, key: String, accentColorHex: String = "#FF7043"): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = ViewGroup.LayoutParams(130, 130)
            background = createRoundedDrawable(accentColorHex, 65f, 180)

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        gameView.inputManager.setButtonPressed(key, true)
                        alpha = 0.5f
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        gameView.inputManager.setButtonPressed(key, false)
                        alpha = 1.0f
                        true
                    }
                    else -> false
                }
            }
        }
    }

    // ==========================================
    // 2. محرر المشهد (Scene Editor)
    // ==========================================
    private fun setupSceneEditor() {
        sceneEditorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#14161E"))
        }
    }

    private fun refreshSceneEditorUI() {
        sceneEditorLayout.removeAllViews()

        val title = TextView(this).apply {
            text = "🎨 محرر الكائنات والمشهد"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        sceneEditorLayout.addView(title)
        sceneEditorLayout.addView(createSpacer(16))

        // قائمة اختيار الكائن لتعديل خصائصه
        val entityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val entitySpinner = Spinner(this).apply {
            val names = gameView.currentScene.entities.map { it.name }
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            val currentIdx = gameView.currentScene.entities.indexOf(selectedEntity)
            if (currentIdx >= 0) setSelection(currentIdx)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    selectedEntity = gameView.currentScene.entities.getOrNull(pos)
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val addBtn = createStyledButton("+ كائن جديد", "#00E676", "#000000") {
            val newEnt = GameEntity(name = "كائن_${gameView.currentScene.entities.size + 1}").apply {
                position.set(200f, 200f)
                color = Color.parseColor("#42A5F5")
                scriptType = ScriptType.ARABIC_DSL
                scriptCode = """في_كل_إطار {
    تحرك في اتجاه يمين بسرعة 100
}"""
            }
            gameView.currentScene.addEntity(newEnt)
            selectedEntity = newEnt
            refreshSceneEditorUI()
            showGameMessage("تمت إضافة كائن جديد: ${newEnt.name}")
        }

        entityRow.addView(entitySpinner)
        entityRow.addView(createSpacer(12))
        entityRow.addView(addBtn)
        sceneEditorLayout.addView(entityRow)
        sceneEditorLayout.addView(createSpacer(24))

        // بطاقة تفاصيل الكائن المختار
        selectedEntity?.let { ent ->
            val detailsCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                background = createRoundedDrawable("#1E202B", 16f)
            }

            detailsCard.addView(createFieldRow("اسم الكائن:", ent.name) { ent.name = it })
            detailsCard.addView(createFieldRow("الموضع س (X):", ent.position.x.toInt().toString()) {
                it.toFloatOrNull()?.let { v -> ent.position.x = v }
            })
            detailsCard.addView(createFieldRow("الموضع ص (Y):", ent.position.y.toInt().toString()) {
                it.toFloatOrNull()?.let { v -> ent.position.y = v }
            })
            detailsCard.addView(createFieldRow("العرض:", ent.width.toInt().toString()) {
                it.toFloatOrNull()?.let { v -> ent.width = v }
            })
            detailsCard.addView(createFieldRow("الارتفاع:", ent.height.toInt().toString()) {
                it.toFloatOrNull()?.let { v -> ent.height = v }
            })

            val solidCheck = CheckBox(this).apply {
                text = "جسم صلب لا ينفذ منه اللاعب (Solid Collider)"
                setTextColor(Color.WHITE)
                isChecked = ent.isSolid
                setOnCheckedChangeListener { _, checked -> ent.isSolid = checked }
            }
            detailsCard.addView(solidCheck)

            val gravCheck = CheckBox(this).apply {
                text = "يتأثر بالجاذبية الأرضية (Gravity)"
                setTextColor(Color.WHITE)
                isChecked = ent.affectedByGravity
                setOnCheckedChangeListener { _, checked -> ent.affectedByGravity = checked }
            }
            detailsCard.addView(gravCheck)

            sceneEditorLayout.addView(detailsCard)
        }
    }

    private fun createFieldRow(label: String, initVal: String, onUpdate: (String) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)

            val lbl = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(240, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val input = EditText(context).apply {
                setText(initVal)
                setTextColor(Color.WHITE)
                textSize = 14f
                setBackgroundColor(Color.parseColor("#272A38"))
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) onUpdate(text.toString())
                }
            }
            addView(lbl)
            addView(input)
        }
    }

    // ==========================================
    // 3. استوديو الأكواد (Code Studio)
    // ==========================================
    private fun setupCodeEditor() {
        codeEditorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#14161E"))
        }

        // شريط الأوامر السريعة بلغة الضاد
        val snippetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 16)
        }
        val snippetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val snippets = listOf(
            "إذا ضغط اللاعب \"s\"" to "إذا ضغط اللاعب \"s\" {\n    اقفز بقوة 400\n}",
            "تحرك في اتجاه" to "تحرك في اتجاه يمين بسرعة 200",
            "إذا توفر عنصر" to "إذا توفر عنصر \"حجر_سحري\" في المخزون {\n    افتح هذا_الكائن\n}",
            "اقفز بقوة" to "اقفز بقوة 380",
            "افتح" to "افتح هذا_الكائن",
            "أضف عنصر" to "أضف عنصر \"مفتاح\" إلى المخزون",
            "شغل صوت" to "شغل صوت \"نغمة_فوز\"",
            "دمر_الكائن" to "دمر_الكائن هذا_الكائن"
        )

        for ((label, code) in snippets) {
            val btn = TextView(this).apply {
                text = label
                textSize = 12.5f
                setTextColor(Color.parseColor("#00E676"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(20, 12, 20, 12)
                background = createRoundedDrawable("#1E202B", 12f)
                setOnClickListener {
                    insertCodeSnippet(code)
                }
            }
            snippetRow.addView(btn)
            snippetRow.addView(createSpacer(10))
        }
        snippetScroll.addView(snippetRow)
        codeEditorLayout.addView(snippetScroll)

        // نوع لغة البرمجة (عربي / بايثون)
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val langLabel = TextView(this).apply {
            text = "لغة السكريبت:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 16, 0)
        }
        langRow.addView(langLabel)

        scriptTypeSpinner = Spinner(this).apply {
            val options = listOf("لغة الضاد (Arabic DSL)", "بايثون (Python)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    selectedEntity?.scriptType = if (pos == 0) ScriptType.ARABIC_DSL else ScriptType.PYTHON
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
        langRow.addView(scriptTypeSpinner)

        val saveCodeBtn = createStyledButton("💾 تطبيق وحفظ الكود", "#FF7043", "#FFFFFF") {
            selectedEntity?.let { ent ->
                ent.scriptCode = codeEditText.text.toString()
                showGameMessage("تم تطبيق الكود على: ${ent.name}")
            }
        }
        langRow.addView(createSpacer(16))
        langRow.addView(saveCodeBtn)

        codeEditorLayout.addView(langRow)

        // محرر الأكواد
        codeEditText = EditText(this).apply {
            setTextColor(Color.parseColor("#E0E6ED"))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(Color.parseColor("#181A22"))
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        codeEditorLayout.addView(codeEditText)
    }

    private fun refreshCodeEditorUI() {
        selectedEntity?.let { ent ->
            codeEditText.setText(ent.scriptCode)
            scriptTypeSpinner.setSelection(if (ent.scriptType == ScriptType.ARABIC_DSL) 0 else 1)
        }
    }

    private fun insertCodeSnippet(code: String) {
        val start = maxOf(codeEditText.selectionStart, 0)
        val end = maxOf(codeEditText.selectionEnd, 0)
        codeEditText.text.replace(minOf(start, end), maxOf(start, end), code, 0, code.length)
    }

    // ==========================================
    // 4. مدير الأصول (Asset Manager)
    // ==========================================
    private fun setupAssetManager() {
        assetManagerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#14161E"))
        }

        val title = TextView(this).apply {
            text = "📁 مدير أصول اللعبة (Sprites & Audio)"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        assetManagerLayout.addView(title)
        assetManagerLayout.addView(createSpacer(12))

        val desc = TextView(this).apply {
            text = "يمكنك استيراد صور الشخصيات والخرائط والمؤثرات الصوتية لدمجها مع كائنات المشهد:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13.5f
        }
        assetManagerLayout.addView(desc)
        assetManagerLayout.addView(createSpacer(20))

        val assetsList = listOf(
            "hero_walk.png" to "شخصية اللاعب (حركة الركض)",
            "magic_stone.png" to "عنصر الحجر السحري",
            "ancient_gate.png" to "بوابة الباب الحجري",
            "jump_sound.ogg" to "مؤثر صوتي للقفز",
            "unlock_gate.ogg" to "مؤثر فتح البوابة الحجرية"
        )

        for ((file, meaning) in assetsList) {
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 18, 24, 18)
                background = createRoundedDrawable("#1E202B", 14f)

                val nameTv = TextView(context).apply {
                    text = "📦 $file"
                    setTextColor(Color.parseColor("#00E676"))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val meaningTv = TextView(context).apply {
                    text = meaning
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12.5f
                }
                addView(nameTv)
                addView(meaningTv)
            }
            assetManagerLayout.addView(itemCard)
            assetManagerLayout.addView(createSpacer(12))
        }

        val importBtn = createStyledButton("＋ إضافة أصل جديد من الهاتف", "#FFA726", "#000000") {
            showGameMessage("تم فتح مستعرض الهاتف لاستيراد الصور والأصوات")
        }
        assetManagerLayout.addView(createSpacer(16))
        assetManagerLayout.addView(importBtn)
    }

    // ==========================================
    // 5. قاموس لغة الضاد (Dictionary)
    // ==========================================
    private fun setupDictionary() {
        dictionaryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#14161E"))
        }

        val title = TextView(this).apply {
            text = "📖 قاموس ومرجع لغة البرمجة العربية"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        dictionaryLayout.addView(title)
        dictionaryLayout.addView(createSpacer(16))

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val itemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (entry in ArabicDictionary.entries) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                background = createRoundedDrawable("#1E202B", 16f)

                val header = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    val keywordTv = TextView(context).apply {
                        text = entry.keyword
                        textSize = 16f
                        setTextColor(Color.parseColor("#00E676"))
                        typeface = Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val categoryBadge = TextView(context).apply {
                        text = entry.category
                        textSize = 11.5f
                        setTextColor(Color.parseColor("#FFA726"))
                        setPadding(16, 6, 16, 6)
                        background = createRoundedDrawable("#2E2B25", 10f)
                    }
                    addView(keywordTv)
                    addView(categoryBadge)
                }
                addView(header)
                addView(createSpacer(10))

                val descTv = TextView(context).apply {
                    text = entry.description
                    textSize = 13.5f
                    setTextColor(Color.parseColor("#DDE5F0"))
                }
                addView(descTv)
                addView(createSpacer(10))

                val syntaxTv = TextView(context).apply {
                    text = "الصيغة: " + entry.syntax
                    textSize = 13f
                    setTextColor(Color.parseColor("#80D8FF"))
                    typeface = Typeface.MONOSPACE
                    setPadding(16, 10, 16, 10)
                    background = createRoundedDrawable("#181A22", 10f)
                }
                addView(syntaxTv)
                addView(createSpacer(10))

                val copyBtn = createStyledButton("📋 نسخ هذا الكود للمحرر", "#37474F", "#FFFFFF") {
                    insertCodeSnippet(entry.example)
                    switchTab(2) // التحويل إلى محرر الكود
                    showGameMessage("تم نسخ الكود بنجاح إلى المحرر!")
                }
                addView(copyBtn)
            }

            itemsContainer.addView(card)
            itemsContainer.addView(createSpacer(14))
        }

        scrollView.addView(itemsContainer)
        dictionaryLayout.addView(scrollView)
    }

    // ==========================================
    // أدوات تنسيق الواجهة
    // ==========================================
    private fun createStyledButton(label: String, bgHex: String, textHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(textHex))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(24, 14, 24, 14)
            background = createRoundedDrawable(bgHex, 14f)
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedDrawable(colorHex: String, radius: Float, alphaVal: Int = 255): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(colorHex))
            alpha = alphaVal
        }
    }

    private fun createSpacer(sizeDp: Int): View {
        return View(this).apply {
            layoutParams = ViewGroup.LayoutParams(sizeDp, sizeDp)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.stop()
    }
}