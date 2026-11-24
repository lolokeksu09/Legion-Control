package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

/** User-started game checks. No settings writes, automatic launch, or service start. */
class GamePreflightActivity : FragmentActivity() {
    companion object {
        const val EXTRA_PACKAGE = "preflight_package"
    }
    private lateinit var model: GamePreflightViewModel
    private lateinit var status: TextView
    private lateinit var selection: TextView
    private lateinit var requirements: TextView
    private lateinit var reportSummary: TextView
    private lateinit var results: LinearLayout
    private lateinit var choose: Button
    private lateinit var refresh: Button
    private lateinit var edit: Button
    private lateinit var check: Button
    private lateinit var launch: Button
    private lateinit var share: Button
    private lateinit var appInfo: Button
    private lateinit var session: Button
    private val Int.dp get() = (this * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[GamePreflightViewModel::class.java]
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(18.dp,18.dp,18.dp,24.dp)
        }
        fun text(value:String,size:Float=14f,color:Int=Color.WHITE):TextView =
            TextView(this).apply {
                this.text=value
                textSize=size
                setTextColor(color)
                setLineSpacing(3f,1f)
                setPadding(0,6.dp,0,10.dp)
                root.addView(this)
            }
        fun button(title:String,featureId:String?=null,action:()->Unit)=Button(this).apply {
            this.text=title
            if(featureId!=null) FeatureReveal.bind(this,featureId)
            LegionUiSupport.styleAction(this)
            setOnClickListener { action() }
            root.addView(this)
        }
        text("GAME PREFLIGHT",22f).apply {
            setTypeface(Typeface.DEFAULT,Typeface.BOLD)
            isAccessibilityHeading=true
        }
        text("Проверьте условия конкретной игры до запуска. Все требования — диагностические пороги, а не изменения Android.",13f,LegionUiSupport.MUTED)
        status=text("Выберите игру.",14f,LegionUiSupport.ACCENT).apply {
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(LegionUiSupport.sectionLabel(this,"ИГРА И ТРЕБОВАНИЯ"))
        selection=text("Игра не выбрана.")
        choose=button("ВЫБРАТЬ ИГРУ ДЛЯ ПРОВЕРКИ", "preflight.game") { chooseApp() }
        refresh=button("Обновить список приложений") { model.loadApps() }
        requirements=text("Требования появятся после выбора игры.")
        edit=button("НАСТРОИТЬ ТРЕБОВАНИЯ ИГРЫ", "preflight.requirements") { configure() }
        check=button("ВЫПОЛНИТЬ ПРОВЕРКУ ПЕРЕД ЗАПУСКОМ", "preflight.check") { model.check() }
        root.addView(LegionUiSupport.sectionLabel(this,"РЕЗУЛЬТАТ ПРОВЕРКИ"))
        reportSummary=text("Проверка ещё не выполнялась.")
        results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(results)
        launch=button("ЗАПУСТИТЬ ПРОВЕРЕННУЮ ИГРУ") { requestLaunch() }
        share=button("Поделиться отчётом проверки", "preflight.report") {
            val report=model.state.value?.report ?: return@button
            open(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,report.text())
            },"Поделиться диагностическим снимком"))
        }
        root.addView(LegionUiSupport.sectionLabel(this,"БЫСТРЫЕ ПЕРЕХОДЫ"))
        button("Настройки энергосбережения") { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
        button("Настройки дисплея") { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        button("Настройки Wi-Fi") { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        appInfo=button("Настройки выбранной игры") {
            val pkg=model.state.value?.selected?.packageName ?: return@button
            open(ApplicationInventory.appInfo(this,pkg))
        }
        button("Открыть Game Toolbox для возврата настроек") {
            open(Intent(this,GameToolboxActivity::class.java))
        }
        session=button("Открыть запись Game Session") {
            val app=model.state.value?.selected ?: return@button
            open(Intent(this,GameSessionActivity::class.java)
                .putExtra("smart_package",app.packageName)
                .putExtra("smart_label",app.label))
        }
        text("Переходы открывают штатные экраны. Legion Control не меняет разрешения, частоту дисплея, CPU/GPU или профиль запуска автоматически.",13f,LegionUiSupport.MUTED)
        LegionUiSupport.setModuleActivityContent(this,ScrollView(this).apply {
            addView(root)
        },"ПРОВЕРКА ПЕРЕД ИГРОЙ")
        model.state.observe(this,::render)
        model.initialize(intent.getStringExtra(EXTRA_PACKAGE)?.takeIf(GameLaunchProfilePolicy::validPackage))
        FeatureReveal.fromActivity(this,root,FeatureModule.PREFLIGHT)
    }

    private fun render(s:PreflightState) {
        status.text=s.message
        selection.text=s.selected?.let { "${it.label}\n${it.packageName}" } ?: "Игра не выбрана."
        requirements.text=if(s.selected==null) "Требования появятся после выбора игры." else with(s.preset) {
            "Дисплей: ${if(targetHz==0) "не задано" else "$targetHz Гц"} · заряд: ≥$minBattery%\n" +
                "Температура: ниже $maxBatteryTempC °C · доступная RAM: ≥$minAvailableRamGiB ГиБ\n" +
                "Проверка интернета Android: ${if(requireValidatedNetwork) "обязательна" else "не требуется"}"
        }
        choose.isEnabled=!s.busy && s.apps.isNotEmpty()
        refresh.isEnabled=!s.busy
        edit.isEnabled=!s.busy && s.selected!=null
        check.isEnabled=edit.isEnabled
        appInfo.isEnabled=edit.isEnabled
        session.isEnabled=edit.isEnabled
        launch.isEnabled=false
        share.isEnabled=!s.busy && s.report!=null
        results.removeAllViews()
        val report=s.report
        if(report==null) {
            reportSummary.text="Проверка ещё не выполнялась."
            return
        }
        val fresh=s.launchFresh && PreflightRules.fresh(report.checkedAtElapsed,SystemClock.elapsedRealtime())
        launch.isEnabled=!s.busy && fresh
        reportSummary.text="Ограничения: ${report.critical} · предупреждения: ${report.warnings} · неизвестно: ${report.unknown}\n" +
            if(fresh) "Снимок выполнен по запросу. Не является FPS или ping игры." else
                "Снимок устарел для запуска. Выполните проверку снова."
        for(f in report.findings) {
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                background=LegionUiSupport.cardSurface(this)
                setPadding(15.dp,12.dp,15.dp,12.dp)
            }
            row.addView(TextView(this).apply {
                text="${f.level.label} · ${f.title}"
                textSize=15f
                setTypeface(Typeface.DEFAULT,Typeface.BOLD)
                setTextColor(when(f.level) {
                    PreflightLevel.CRITICAL -> 0xFFFF8B8B.toInt()
                    PreflightLevel.WARNING -> 0xFFFFCF87.toInt()
                    PreflightLevel.PASS -> 0xFFA7DBB4.toInt()
                    else -> LegionUiSupport.MUTED
                })
            })
            row.addView(TextView(this).apply {
                text=f.detail
                textSize=13f
                setTextColor(Color.WHITE)
                setPadding(0,5.dp,0,0)
                setLineSpacing(3f,1f)
            })
            results.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8.dp })
        }
    }

    private fun chooseApp() {
        val apps=model.state.value?.apps.orEmpty()
        if(apps.isEmpty()) return
        val labels=apps.associateBy { "${it.label}\n${it.packageName}" }
        val content=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(16.dp,10.dp,16.dp,0)
        }
        val search=EditText(this).apply {
            hint="Название игры или пакет"
            isSingleLine=true
        }
        val adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,labels.keys.toList())
        val list=ListView(this).apply { this.adapter=adapter }
        content.addView(search,LinearLayout.LayoutParams(-1,-2))
        content.addView(list,LinearLayout.LayoutParams(-1,320.dp))
        val dialog=AlertDialog.Builder(this).setTitle("Выбрать игру").setView(content)
            .setNegativeButton("Отмена",null).create()
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s:Editable?) {}
        })
        list.setOnItemClickListener { _,_,position,_ ->
            val app=adapter.getItem(position)?.let(labels::get) ?: return@setOnItemClickListener
            model.select(app.packageName)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun configure() {
        val current=model.state.value ?: return
        if(current.selected==null || current.busy) return
        val preset=current.preset
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,6.dp,18.dp,14.dp)
        }
        fun selector(title:String,values:List<Int>,selected:Int,format:(Int)->String):Spinner {
            val caption=TextView(this).apply { text=title; textSize=14f; setPadding(0,12.dp,0,3.dp) }
            panel.addView(caption)
            return Spinner(this).apply {
                id=View.generateViewId()
                caption.labelFor=id
                minimumHeight=48.dp
                adapter=ArrayAdapter(this@GamePreflightActivity,android.R.layout.simple_spinner_item,
                    values.map(format)).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(values.indexOf(selected).coerceAtLeast(0))
                panel.addView(this,LinearLayout.LayoutParams(-1,-2))
            }
        }
        val hz=listOf(0,60,90,120,144,165)
        val battery=(5..80).toList()
        val temperature=(35..45).toList()
        val ram=(0..8).toList()
        val hzField=selector("Целевая частота дисплея",hz,preset.targetHz) { if(it==0) "Не проверять" else "$it Гц" }
        val batteryField=selector("Минимальный заряд",battery,preset.minBattery) { "$it%" }
        val tempField=selector("Порог температуры батареи",temperature,preset.maxBatteryTempC) { "$it °C" }
        val ramField=selector("Минимальная доступная RAM",ram,preset.minAvailableRamGiB) {
            if(it==0) "Не проверять минимум" else "$it ГиБ"
        }
        val network=CheckBox(this).apply {
            text="Требовать подтверждённый интернет Android"
            isChecked=preset.requireValidatedNetwork
        }
        panel.addView(network)
        AlertDialog.Builder(this).setTitle("Требования ${current.selected.label}")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("Сохранить") { _,_ ->
                model.savePreset(PreflightPreset(hz[hzField.selectedItemPosition],
                    battery[batteryField.selectedItemPosition],temperature[tempField.selectedItemPosition],
                    ram[ramField.selectedItemPosition],network.isChecked))
            }.setNegativeButton("Отмена",null).show()
    }

    private fun requestLaunch() {
        val state=model.state.value ?: return
        val report=state.report ?: return
        if(!state.launchFresh || !PreflightRules.fresh(report.checkedAtElapsed,SystemClock.elapsedRealtime())) {
            status.text="Снимок устарел. Выполните проверку снова."
            return
        }
        if(report.critical>0) {
            AlertDialog.Builder(this).setTitle("Есть тепловые ограничения")
                .setMessage("Отложите нагрузку. Дайте устройству остыть и повторите проверку.")
                .setPositiveButton("Понятно",null).show()
            return
        }
        val run={ launchChecked(report) }
        if(report.warnings>0 || report.unknown>0) {
            AlertDialog.Builder(this).setTitle("Запустить игру?")
                .setMessage("Предупреждений: ${report.warnings}, неизвестных показателей: ${report.unknown}. " +
                    "Запуск не применяет системные настройки автоматически.")
                .setPositiveButton("Запустить") { _,_-> run() }
                .setNegativeButton("Отмена",null).show()
        } else run()
    }

    private fun launchChecked(report:PreflightReport) {
        val s=model.state.value ?: return
        if(s.busy || !s.launchFresh || s.report!==report || s.selected?.packageName!=report.packageName ||
            !PreflightRules.fresh(report.checkedAtElapsed,SystemClock.elapsedRealtime())) {
            status.text="Игра или проверка изменились. Выполните проверку снова."
            return
        }
        val intent=runCatching { ApplicationInventory.launchIntent(this,report.packageName) }.getOrNull()
        if(intent==null) status.text="Android больше не предоставляет Launcher Intent."
        else open(intent)
    }

    private fun open(intent:Intent) {
        runCatching { startActivity(intent) }.onFailure {
            status.text="Android не открыл экран: " + it.javaClass.simpleName
        }
    }

    override fun onStart() {
        super.onStart()
        model.setVisible(true)
    }

    override fun onStop() {
        model.setVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if(::model.isInitialized) model.state.value?.let(::render)
    }
}
