package dev.lolokeksu.legioncontrol

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import java.text.DateFormat
import java.util.Date

class SessionLibraryActivity:FragmentActivity() {
    companion object {
        const val EXTRA_PACKAGE="library_package"
        const val EXTRA_SESSION="analytics_session"
    }
    private lateinit var vm:SessionLibraryViewModel
    private lateinit var status:TextView
    private lateinit var filters:TextView
    private lateinit var list:LinearLayout
    private lateinit var detail:TextView
    private lateinit var game:Button
    private lateinit var period:Button
    private lateinit var stateButton:Button
    private lateinit var refresh:Button
    private lateinit var next:Button
    private lateinit var previous:Button
    private lateinit var export:Button
    private var lastDetail:LibraryDetail?=null
    private lateinit var selector:Spinner
    private lateinit var panels:Map<AnalyticsPage,LinearLayout>
    private lateinit var selected:TextView
    private lateinit var signals:TextView
    private lateinit var incidents:TextView
    private lateinit var comparison:TextView
    private lateinit var baseline:TextView
    private lateinit var chart:SessionCompareChart
    private lateinit var comparisonChoice:Button
    private lateinit var windowButton:Button
    private lateinit var chartButton:Button
    private val reportActions=mutableListOf<Button>()
    private lateinit var csvSignals:Button
    private lateinit var csvIncidents:Button
    private lateinit var csvCompare:Button
    private lateinit var csvBaseline:Button
    private lateinit var compareRun:Button
    private lateinit var shareCompare:Button
    private var compareId:String?=null
    private var minutes=10
    private var incidentKind:SessionIncidents.Kind?=null
    private var graph=CompareGraph.TEMP
    private var page=AnalyticsPage.LIBRARY
    private val csvDocument=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(!::vm.isInitialized)return@registerForActivityResult
        val snapshot=vm.pendingCsv.take()
        if(uri!=null && snapshot!=null)vm.exportCsv(uri,snapshot)
        vm.state.value?.let(::render)
        if(uri!=null && snapshot==null && ::status.isInitialized)status.text="Отчёт недоступен. Повторите экспорт."
    }
    private val Int.dp get()=(this*resources.displayMetrics.density+.5f).toInt()
    private val document=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(!::vm.isInitialized) return@registerForActivityResult
        val selected=vm.pendingDocument.take()
        val current=vm.state.value
        if(uri==null) {
            current?.let(::render)
        } else if(selected!=null && current!=null && current.detail===selected && !current.busy) {
            vm.export(uri,selected)
        } else {
            current?.let(::render)
            if(::status.isInitialized)status.text="Исходный результат недоступен. Повторите экспорт."
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        vm=ViewModelProvider(this)[SessionLibraryViewModel::class.java]
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,18.dp,18.dp,24.dp)
            setBackgroundColor(Color.BLACK)
        }
        var target=root
        fun label(value:String,size:Float=14f,color:Int=Color.WHITE)=TextView(this).apply {
            text=value;textSize=size;setTextColor(color);setLineSpacing(3f,1f)
            setPadding(0,5.dp,0,10.dp);target.addView(this)
        }
        fun button(title:String,featureId:String?=null,action:()->Unit)=Button(this).apply {
            text=title;if(featureId!=null)FeatureReveal.bind(this,featureId);LegionUiSupport.styleAction(this);setOnClickListener { action() };target.addView(this)
        }
        label("Игровая аналитика",22f).apply { setTypeface(Typeface.DEFAULT,Typeface.BOLD);isAccessibilityHeading=true }
        label("Выберите игру и запись один раз. Отчёты используют общую локальную телеметрию.",13f,LegionUiSupport.MUTED)
        status=label("Загрузка истории…",14f,LegionUiSupport.ACCENT)
        selected=label("Сессия не выбрана.").apply { LegionUiSupport.reportSurface(this) }
        selector=LegionUiSupport.spinner(this,AnalyticsPage.entries.map { it.label })
        root.addView(selector)
        panels=AnalyticsPage.entries.associateWith {
            LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        }
        panels.values.forEach { root.addView(it) }
        target=panels.getValue(AnalyticsPage.LIBRARY)
        target.addView(LegionUiSupport.sectionLabel(this,"ФИЛЬТРЫ"))
        filters=label("").apply { LegionUiSupport.reportSurface(this) }
        game=button("Фильтр по игре", "library.game") { chooseGame() }
        period=button("Фильтр по периоду", "library.period") {
            val s=vm.state.value ?: return@button
            val options=LibraryPeriod.entries
            LegionDialogs.builder(this).setTitle("Период").setItems(options.map { it.label }.toTypedArray()) { _,i->
                vm.refresh(s.filter.copy(period=options[i]))
            }.setNegativeButton("Отмена",null).show()
        }
        stateButton=button("Фильтр по завершению", "library.state") {
            val s=vm.state.value ?: return@button
            val options=LibraryStatus.entries
            LegionDialogs.builder(this).setTitle("Состояние записи").setItems(options.map { it.label }.toTypedArray()) { _,i->
                vm.refresh(s.filter.copy(status=options[i]))
            }.setNegativeButton("Отмена",null).show()
        }
        refresh=button("Обновить библиотеку сессий") { vm.refresh() }
        target.addView(LegionUiSupport.sectionLabel(this,"ЗАПИСИ"))
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        target.addView(list)
        previous=button("Предыдущая страница") { vm.previous() }
        next=button("Следующая страница") { vm.next() }
        target=panels.getValue(AnalyticsPage.OVERVIEW)
        target.addView(LegionUiSupport.sectionLabel(this,"ВЫБРАННАЯ ЗАПИСЬ"))
        detail=label("Выберите завершённую запись.").apply { setTextIsSelectable(true); LegionUiSupport.reportSurface(this) }
        export=button("ЭКСПОРТ ВЫБРАННОЙ СЕССИИ ZIP", "library.zip") {
            val selected=vm.state.value?.detail ?: return@button
            if(!vm.pendingDocument.begin(selected)) return@button
            export.isEnabled=false
            runCatching { document.launch("legion-session-${selected.header.id.take(8)}.zip") }
                .onFailure {
                    vm.pendingDocument.take()
                    vm.state.value?.let(::render)
                    status.text="Не удалось открыть выбор документа."
                }
        }
        button("Выбрать другую сессию") { showPage(AnalyticsPage.LIBRARY) }
        target=panels.getValue(AnalyticsPage.SIGNALS)
        signals=label("Выберите завершённую сессию в библиотеке.").apply {
            setTextIsSelectable(true);LegionUiSupport.reportSurface(this)
        }
        reportActions += button("Обновить хронологию","signals.timeline") {
            val s=vm.state.value ?: return@button
            val id=s.detail?.header?.id ?: return@button
            vm.refreshSelected(id)
        }
        csvSignals=button("Сохранить события выбранной сессии CSV","signals.csv") {
            vm.state.value?.analytics?.let { exportCsv("signals",SessionSignals.csv(it.signals)) }
        }

        target=panels.getValue(AnalyticsPage.INCIDENTS)
        incidents=label("Выберите завершённую сессию в библиотеке.").apply {
            setTextIsSelectable(true);LegionUiSupport.reportSurface(this)
        }
        reportActions += button("Фильтр категорий","incidents.filter") {
            val kinds=SessionIncidents.Kind.entries
            LegionDialogs.builder(this).setTitle("Категория проблем")
                .setItems((listOf("Все категории")+kinds.map { it.label }).toTypedArray()) { _,i ->
                    incidentKind=kinds.getOrNull(i-1)
                    vm.state.value?.let(::renderReports)
                }.setNegativeButton("Отмена",null).show()
        }
        csvIncidents=button("Сохранить эпизоды CSV","incidents.csv") {
            vm.state.value?.analytics?.let { exportCsv("incidents",SessionIncidents.csv(it.incidents,incidentKind)) }
        }

        target=panels.getValue(AnalyticsPage.COMPARE)
        label("A — выбранная сессия; B — другая завершённая запись той же игры.",13f,LegionUiSupport.MUTED)
        button("ВЫБРАТЬ ИГРУ ИЗ ИСТОРИИ","compare.game") { chooseGame() }
        comparisonChoice=button("Выбрать вторую сессию") { chooseComparison() }
        windowButton=button("Выбрать окно сравнения","compare.window") {
            val options=SessionCompareAnalysis.windows
            LegionDialogs.builder(this).setTitle("Одинаковое окно")
                .setItems(options.map { "$it мин" }.toTypedArray()) { _,i ->
                    minutes=options[i];vm.clearComparison()
                }.setNegativeButton("Отмена",null).show()
        }
        compareRun=button("СРАВНИТЬ ВЫБРАННЫЕ СЕССИИ","compare.run") {
            compareId?.let { vm.compareWith(it,minutes) }
        }
        chartButton=button("Выбрать показатель сравнения","compare.graph") {
            val choices=CompareGraph.entries
            LegionDialogs.builder(this).setTitle("Показатель графика")
                .setItems(choices.map { it.label }.toTypedArray()) { _,i ->
                    graph=choices[i];vm.state.value?.let(::renderReports)
                }.setNegativeButton("Отмена",null).show()
        }
        chart=SessionCompareChart(this)
        target.addView(chart,LinearLayout.LayoutParams(-1,180.dp))
        comparison=label("Сравнение не выполнялось.").apply {
            setTextIsSelectable(true);LegionUiSupport.reportSurface(this)
        }
        csvCompare=button("СОХРАНИТЬ СРАВНЕНИЕ CSV","compare.csv") {
            vm.state.value?.pair?.let { exportCsv("compare",SessionCompareAnalysis.csv(it)) }
        }
        shareCompare=button("Поделиться сравнением сессий") {
            vm.state.value?.pair?.let { pair ->
                runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="text/plain";putExtra(Intent.EXTRA_TEXT,SessionCompareAnalysis.report(pair))
                },"Поделиться сравнением")) }.onFailure { status.text="Нет приложения для отправки." }
            }
        }

        target=panels.getValue(AnalyticsPage.BASELINES)
        label("Базовые показатели сравнивают новейшую запись игры с её историей, а не произвольную сессию.",13f,LegionUiSupport.MUTED)
        reportActions += button("За последние 7 дней","baselines.week") { vm.baseline(7) }
        reportActions += button("За последние 30 дней","baselines.month") { vm.baseline(30) }
        baseline=label("Выберите игру и завершённую сессию.").apply {
            setTextIsSelectable(true);LegionUiSupport.reportSurface(this)
        }
        csvBaseline=button("Экспортировать сравнение CSV","baselines.csv") {
            vm.state.value?.baseline?.let { exportCsv("baseline",SessionBaselines.csv(it)) }
        }
        target=panels.getValue(AnalyticsPage.OVERVIEW)
        button("Эксперименты A/B и заметки") {
            val pkg=vm.state.value?.detail?.header?.packageName ?: vm.state.value?.filter?.packageName
            startActivity(Intent(this,ExperimentLabActivity::class.java).apply {
                pkg?.let { putExtra("experiment_package",it) }
            })
        }
        LegionUiSupport.setModuleActivityContent(this,ScrollView(this).apply { addView(root) },"ИГРОВАЯ АНАЛИТИКА")
        val featureId=intent.getStringExtra(FeatureFinder.EXTRA_FEATURE_ID)
        val featureModule=FeatureFinder.entries.firstOrNull { it.id==featureId }?.module
        minutes=savedInstanceState?.getInt("analytics_minutes",10)?.takeIf { it in SessionCompareAnalysis.windows } ?: 10
        compareId=savedInstanceState?.getString("analytics_compare_id")
        graph=CompareGraph.entries.firstOrNull { it.name==savedInstanceState?.getString("analytics_graph") } ?: CompareGraph.TEMP
        incidentKind=SessionIncidents.Kind.entries.firstOrNull { it.name==savedInstanceState?.getString("analytics_incident") }
        val initial=AnalyticsPage.entries.firstOrNull { it.name==savedInstanceState?.getString("analytics_page") }
            ?: AnalyticsPage.forFeature(featureId) ?: AnalyticsPage.LIBRARY
        showPage(initial)
        selector.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                AnalyticsPage.entries.getOrNull(position)?.let(::showPage)
            }
        }
        lastDetail=vm.state.value?.detail
        vm.state.observe(this,::render)
        vm.initialize(intent.getStringExtra(EXTRA_PACKAGE),intent.getStringExtra(EXTRA_SESSION))
        if(featureModule!=null)FeatureReveal.fromActivity(this,root,featureModule)
        else FeatureReveal.fromActivity(this,root,FeatureModule.LIBRARY)
    }
    private fun render(s:LibraryState) {
        status.text=s.message
        LegionUiSupport.notice(status, if(s.busy) LegionUiSupport.Notice.LOADING else LegionUiSupport.Notice.INFO)
        filters.text="${s.filter.packageName ?: "Все игры"} · ${s.filter.period.label} · ${s.filter.status.label}\nСтраница ${s.previous.size+1}"
        val available=!s.busy && !vm.pendingCsv.hasPending && !vm.pendingDocument.hasPending
        game.isEnabled=available;period.isEnabled=available;stateButton.isEnabled=available;refresh.isEnabled=available
        previous.isEnabled=available && s.previous.isNotEmpty()
        next.isEnabled=available && s.next!=null && s.previous.size<499
        if(s.next!=null && s.previous.size>=499) filters.append("\nЛимит 500 страниц. Уточните фильтр.")
        export.isEnabled=available && s.detail!=null
        list.removeAllViews()
        if(s.rows.isEmpty())list.addView(LegionUiSupport.noticeView(this,
            if(s.busy) "Читаю локальные записи…" else "Записей нет.",
            if(s.busy) LegionUiSupport.Notice.LOADING else LegionUiSupport.Notice.EMPTY))
        val date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        for(row in s.rows) {
            val end=row.endedAtMs?.let { "${(it-row.startedAtMs).coerceAtLeast(0)/60000} мин · ${row.endReason ?: "н/д"}" } ?: "Нет завершения"
            list.addView(LegionUiSupport.actionTile(this,row.appLabel,
                "${row.packageName} · ${date.format(Date(row.startedAtMs))}\n$end · ${row.id.take(8)}") {
                if(available || s.selecting)vm.select(row.id)
            },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8.dp })
        }
        val d=s.detail
        val newlySelected=d!=null && d!==lastDetail
        if(newlySelected && d?.header?.id!=lastDetail?.header?.id) { compareId=null;incidentKind=null }
        lastDetail=d
        detail.text=if(d==null)"Выберите завершённую запись." else buildString {
            append(GameSessionAnalysis.render(s.analytics?.summary ?: GameSessionAnalysis.summarize(d.header,d.points)))
            append("\n\nСобытий в архиве: ").append(d.events.size)
            if(d.sampleLimited || d.eventLimited)append("\nДостигнут лимит чтения: архив может быть неполным.")
            append("\nПоследние 10 событий из доступного окна:\n")
            d.events.takeLast(10).forEach { append(date.format(Date(it.timeMs))).append(" · ").append(it.kind)
                .append(" · ").append(it.details).append('\n') }
        }
        selected.text=d?.header?.let { "${it.appLabel} · ${it.id.take(8)}\n${date.format(Date(it.startedAtMs))}" }
            ?: "Сессия не выбрана."
        renderReports(s)
        if(newlySelected && page==AnalyticsPage.LIBRARY)showPage(AnalyticsPage.OVERVIEW)
    }

    private fun showPage(next:AnalyticsPage) {
        page=next
        panels.forEach { (key,panel) -> panel.visibility=if(key==next)View.VISIBLE else View.GONE }
        if(selector.selectedItemPosition!=next.ordinal)selector.setSelection(next.ordinal)
    }

    private fun chooseGame() {
        val s=vm.state.value ?: return
        if(s.busy || vm.pendingCsv.hasPending || vm.pendingDocument.hasPending)return
        val packages=(s.packages+listOfNotNull(s.filter.packageName)).distinct()
        LegionDialogs.builder(this).setTitle("Игра")
            .setItems((listOf("Все игры")+packages).toTypedArray()) { _,i ->
                showPage(AnalyticsPage.LIBRARY)
                vm.refresh(s.filter.copy(packageName=packages.getOrNull(i-1)))
            }.setNegativeButton("Отмена",null).show()
    }

    private fun chooseComparison() {
        val rows=vm.state.value?.comparisonRows.orEmpty()
        if(rows.isEmpty())return
        val date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        LegionDialogs.builder(this).setTitle("Вторая сессия той же игры")
            .setItems(rows.map { "${date.format(Date(it.startedAtMs))} · ${it.id.take(8)}" }.toTypedArray()) { _,i ->
                compareId=rows[i].id;vm.clearComparison()
            }.setNegativeButton("Отмена",null).show()
    }

    private fun renderReports(s:LibraryState) {
        val snapshot=s.analytics
        val enabled=!s.busy && !vm.pendingCsv.hasPending && !vm.pendingDocument.hasPending
        if(compareId !in s.comparisonRows.map { it.id })compareId=null
        signals.text=snapshot?.let { SessionSignals.render(it.signals) } ?: "Выберите завершённую сессию."
        incidents.text=snapshot?.let { SessionIncidents.render(it.incidents,incidentKind) } ?: "Выберите завершённую сессию."
        comparison.text=s.pair?.let(SessionCompareAnalysis::report) ?: "Выберите вторую сессию и окно: $minutes мин."
        baseline.text=s.baseline?.let(SessionBaselines::render) ?: "Выберите период для анализа истории игры."
        comparisonChoice.text="Вторая сессия: ${compareId?.take(8) ?: "выбрать"}"
        comparisonChoice.contentDescription=comparisonChoice.text
        windowButton.contentDescription="Окно сравнения: $minutes мин"
        chartButton.contentDescription="Показатель сравнения: ${graph.label}"
        chart.update(s.pair,graph)
        chart.visibility=if(s.pair!=null)View.VISIBLE else View.GONE
        reportActions.forEach { it.isEnabled=enabled && snapshot!=null }
        comparisonChoice.isEnabled=enabled && s.comparisonRows.isNotEmpty()
        windowButton.isEnabled=enabled && snapshot!=null
        chartButton.isEnabled=enabled && s.pair!=null
        compareRun.isEnabled=enabled && snapshot!=null && compareId!=null
        csvSignals.isEnabled=enabled && snapshot!=null
        csvIncidents.isEnabled=enabled && snapshot!=null
        csvCompare.isEnabled=enabled && s.pair!=null
        shareCompare.isEnabled=enabled && s.pair!=null
        csvBaseline.isEnabled=enabled && s.baseline!=null
    }

    private fun exportCsv(kind:String,content:String) {
        val s=vm.state.value ?: return
        if(s.busy || vm.pendingDocument.hasPending)return
        val id=s.detail?.header?.id ?: return
        val snapshot=AnalyticsCsv("legion-$kind-${id.take(8)}.csv",content)
        if(!vm.pendingCsv.begin(snapshot))return
        render(s)
        runCatching { csvDocument.launch(snapshot.filename) }.onFailure {
            vm.pendingCsv.take();render(s);status.text="Не удалось открыть выбор документа."
        }
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("analytics_page",page.name)
        outState.putString("analytics_compare_id",compareId)
        outState.putInt("analytics_minutes",minutes)
        outState.putString("analytics_graph",graph.name)
        outState.putString("analytics_incident",incidentKind?.name)
        super.onSaveInstanceState(outState)
    }
}
