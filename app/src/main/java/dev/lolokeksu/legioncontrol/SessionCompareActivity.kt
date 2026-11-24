package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import java.text.DateFormat
import java.util.Date

class SessionCompareActivity:FragmentActivity() {
    companion object { const val EXTRA_PACKAGE="compare_package" }
    private lateinit var vm:SessionCompareViewModel
    private lateinit var status:TextView
    private lateinit var selection:TextView
    private lateinit var choose:Button
    private lateinit var refresh:Button
    private lateinit var a:Button
    private lateinit var b:Button
    private lateinit var swap:Button
    private lateinit var window:Button
    private lateinit var run:Button
    private lateinit var graphButton:Button
    private lateinit var export:Button
    private lateinit var share:Button
    private lateinit var graphSummary:TextView
    private lateinit var output:TextView
    private lateinit var chart:SessionCompareChart
    private var metric=CompareGraph.TEMP
    private val Int.dp get()=(this*resources.displayMetrics.density+.5f).toInt()
    private val document=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri->
        if(!::vm.isInitialized) return@registerForActivityResult
        val pair=vm.pendingDocument.take()
        val current=vm.state.value
        if(uri==null) {
            current?.let(::render)
        } else if(pair!=null && current!=null && current.pair===pair && !current.busy) {
            vm.export(uri,pair)
        } else {
            current?.let(::render)
            if(::status.isInitialized)status.text="Исходный результат недоступен. Повторите экспорт."
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        vm=ViewModelProvider(this)[SessionCompareViewModel::class.java]
        metric=savedInstanceState?.getString("graph")?.let { runCatching { CompareGraph.valueOf(it) }.getOrNull() }
            ?:CompareGraph.TEMP
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,18.dp,18.dp,24.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun label(value:String,size:Float=14f,color:Int=Color.WHITE)=TextView(this).apply {
            text=value;textSize=size;setTextColor(color);setLineSpacing(3f,1f)
            setPadding(0,5.dp,0,10.dp)
            root.addView(this)
        }
        fun button(title:String,action:()->Unit)=Button(this).apply {
            text=title;LegionUiSupport.styleAction(this);setOnClickListener { action() };root.addView(this)
        }
        label("SESSION COMPARE",22f).apply {
            setTypeface(Typeface.DEFAULT,Typeface.BOLD);isAccessibilityHeading=true
        }
        label("Сравните одинаковые интервалы двух записей одной игры. Только локальные замеры; не FPS или ping игры.",13f,LegionUiSupport.MUTED)
        status=label("Загрузка истории…",14f,LegionUiSupport.ACCENT)
        root.addView(LegionUiSupport.sectionLabel(this,"ИСТОЧНИКИ"))
        selection=label("История не выбрана.")
        choose=button("ВЫБРАТЬ ИГРУ ИЗ ИСТОРИИ") {
            val choices=vm.state.value?.packages.orEmpty()
            if(choices.isNotEmpty())AlertDialog.Builder(this).setTitle("Игра из истории")
                .setItems(choices.toTypedArray()) { _,i->vm.choosePackage(choices[i]) }
                .setNegativeButton("Отмена",null).show()
        }
        refresh=button("Обновить локальную историю") { vm.reload() }
        a=button("ВЫБРАТЬ СЕССИЮ A") { pick(true) }
        b=button("ВЫБРАТЬ СЕССИЮ B") { pick(false) }
        swap=button("Поменять A и B местами") { vm.swap() }
        window=button("Выбрать окно сравнения") {
            val options=SessionCompareAnalysis.windows
            AlertDialog.Builder(this).setTitle("Первые минуты каждой сессии")
                .setItems(options.map { "$it мин" }.toTypedArray()) { _,i->vm.chooseWindow(options[i]) }
                .setNegativeButton("Отмена",null).show()
        }
        run=button("СРАВНИТЬ ВЫБРАННЫЕ СЕССИИ") { vm.compare() }
        root.addView(LegionUiSupport.sectionLabel(this,"СОВМЕЩЁННЫЙ ГРАФИК"))
        graphButton=button("Выбрать показатель сравнения") {
            val options=CompareGraph.entries
            AlertDialog.Builder(this).setTitle("Показатель")
                .setItems(options.map { it.label }.toTypedArray()) { _,i->
                    metric=options[i];vm.state.value?.let(::render)
                }.setNegativeButton("Отмена",null).show()
        }
        graphSummary=label("Синий — A, золотой — B. Нет данных.",13f,LegionUiSupport.MUTED)
        chart=SessionCompareChart(this)
        root.addView(chart,LinearLayout.LayoutParams(-1,180.dp).apply { bottomMargin=12.dp })
        root.addView(LegionUiSupport.sectionLabel(this,"РЕЗУЛЬТАТ"))
        output=label("Сравнение не выполнялось.").apply { setTextIsSelectable(true) }
        export=button("СОХРАНИТЬ СРАВНЕНИЕ CSV") {
            val pair=vm.state.value?.pair ?: return@button
            if(!vm.pendingDocument.begin(pair)) return@button
            export.isEnabled=false
            runCatching { document.launch("legion-session-compare-${System.currentTimeMillis()}.csv") }
                .onFailure {
                    vm.pendingDocument.take()
                    vm.state.value?.let(::render)
                    status.text="Не удалось открыть выбор документа."
                }
        }
        share=button("Поделиться сравнением сессий") {
            val pair=vm.state.value?.pair ?: return@button
            runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,SessionCompareAnalysis.report(pair))
            },"Поделиться сравнением")) }.onFailure { status.text="Не удалось открыть меню отправки." }
        }
        LegionUiSupport.setModuleActivityContent(this,ScrollView(this).apply { addView(root) },"СРАВНЕНИЕ ИГРОВЫХ СЕССИЙ")
        vm.state.observe(this,::render)
        vm.initialize(intent.getStringExtra(EXTRA_PACKAGE))
        FeatureReveal.fromActivity(this,root,FeatureModule.COMPARE)
    }

    private fun name(h:GameSessionHeader):String {
        val date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(h.startedAtMs))
        val minutes=((h.endedAtMs ?: h.startedAtMs)-h.startedAtMs).coerceAtLeast(0)/60000
        return "${h.appLabel} · $date · $minutes мин · ${h.endReason ?: "н/д"} · ${h.id.take(8)}"
    }
    private fun pick(sideA:Boolean) {
        val s=vm.state.value ?: return
        val options=s.sessions.filter { it.id!=(if(sideA)s.bId else s.aId) }
        if(options.isEmpty())return
        AlertDialog.Builder(this).setTitle(if(sideA)"Сессия A" else "Сессия B")
            .setItems(options.map(::name).toTypedArray()) { _,i->vm.chooseSession(sideA,options[i].id) }
            .setNegativeButton("Отмена",null).show()
    }
    private fun render(s:CompareState) {
        status.text=s.message
        selection.text="Игра: ${s.packageName ?: "не выбрана"}\n" +
            "A: ${s.sessions.firstOrNull { it.id==s.aId }?.let(::name) ?: "не выбрана"}\n" +
            "B: ${s.sessions.firstOrNull { it.id==s.bId }?.let(::name) ?: "не выбрана"}\n" +
            "Окно: первые ${s.minutes} мин каждой записи."
        choose.isEnabled=!s.busy && s.packages.isNotEmpty()
        refresh.isEnabled=!s.busy
        a.isEnabled=!s.busy && s.sessions.size>=2;b.isEnabled=a.isEnabled
        swap.isEnabled=!s.busy && s.aId!=null && s.bId!=null
        window.isEnabled=!s.busy
        run.isEnabled=swap.isEnabled
        export.isEnabled=!s.busy && s.pair!=null && !vm.pendingDocument.hasPending
        share.isEnabled=!s.busy && s.pair!=null
        val pair=s.pair
        val valuesA=pair?.a?.series.orEmpty().mapNotNull(metric::value)
        val valuesB=pair?.b?.series.orEmpty().mapNotNull(metric::value)
        fun range(v:List<Double>)=if(v.isEmpty())"н/д" else
            "${SessionCompareAnalysis.format(v.minOrNull())}–${SessionCompareAnalysis.format(v.maxOrNull())} ${metric.unit}"
        graphSummary.text="${metric.label} · общая шкала\nСиний A: ${range(valuesA)}\nЗолотой B: ${range(valuesB)}"
        chart.update(pair,metric)
        output.text=pair?.let(SessionCompareAnalysis::report) ?: "Сравнение не выполнялось."
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("graph",metric.name)
        super.onSaveInstanceState(outState)
    }
}
