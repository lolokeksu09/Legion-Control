package dev.lolokeksu.legioncontrol

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

class SessionLibraryActivity:FragmentActivity() {
    companion object { const val EXTRA_PACKAGE="library_package" }
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
        fun label(value:String,size:Float=14f,color:Int=Color.WHITE)=TextView(this).apply {
            text=value;textSize=size;setTextColor(color);setLineSpacing(3f,1f)
            setPadding(0,5.dp,0,10.dp);root.addView(this)
        }
        fun button(title:String,featureId:String?=null,action:()->Unit)=Button(this).apply {
            text=title;if(featureId!=null)FeatureReveal.bind(this,featureId);LegionUiSupport.styleAction(this);setOnClickListener { action() };root.addView(this)
        }
        label("SESSION LIBRARY",22f).apply { setTypeface(Typeface.DEFAULT,Typeface.BOLD);isAccessibilityHeading=true }
        label("Локальная история: фильтры, страницы и отдельный архив записи. Данные не удаляются.",13f,LegionUiSupport.MUTED)
        status=label("Загрузка истории…",14f,LegionUiSupport.ACCENT)
        root.addView(LegionUiSupport.sectionLabel(this,"ФИЛЬТРЫ"))
        filters=label("").apply { LegionUiSupport.reportSurface(this) }
        game=button("Фильтр по игре", "library.game") {
            val s=vm.state.value ?: return@button
            val packages=(s.packages+listOfNotNull(s.filter.packageName)).distinct()
            LegionDialogs.builder(this).setTitle("Игра")
                .setItems((listOf("Все игры")+packages).toTypedArray()) { _,i ->
                    vm.refresh(s.filter.copy(packageName=packages.getOrNull(i-1)))
                }.setNegativeButton("Отмена",null).show()
        }
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
        root.addView(LegionUiSupport.sectionLabel(this,"ЗАПИСИ"))
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(list)
        previous=button("Предыдущая страница") { vm.previous() }
        next=button("Следующая страница") { vm.next() }
        root.addView(LegionUiSupport.sectionLabel(this,"ВЫБРАННАЯ ЗАПИСЬ"))
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
        LegionUiSupport.setModuleActivityContent(this,ScrollView(this).apply { addView(root) },"БИБЛИОТЕКА ИГРОВЫХ СЕССИЙ")
        vm.state.observe(this,::render)
        vm.initialize(intent.getStringExtra(EXTRA_PACKAGE))
        FeatureReveal.fromActivity(this,root,FeatureModule.LIBRARY)
    }
    private fun render(s:LibraryState) {
        status.text=s.message
        LegionUiSupport.notice(status, if(s.busy) LegionUiSupport.Notice.LOADING else LegionUiSupport.Notice.INFO)
        filters.text="${s.filter.packageName ?: "Все игры"} · ${s.filter.period.label} · ${s.filter.status.label}\nСтраница ${s.previous.size+1}"
        game.isEnabled=!s.busy;period.isEnabled=!s.busy;stateButton.isEnabled=!s.busy;refresh.isEnabled=!s.busy
        previous.isEnabled=!s.busy && s.previous.isNotEmpty()
        next.isEnabled=!s.busy && s.next!=null && s.previous.size<499
        if(s.next!=null && s.previous.size>=499) filters.append("\nЛимит 500 страниц. Уточните фильтр.")
        export.isEnabled=!s.busy && s.detail!=null && !vm.pendingDocument.hasPending
        list.removeAllViews()
        if(s.rows.isEmpty())list.addView(LegionUiSupport.noticeView(this,
            if(s.busy) "Читаю локальные записи…" else "Записей нет.",
            if(s.busy) LegionUiSupport.Notice.LOADING else LegionUiSupport.Notice.EMPTY))
        val date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        for(row in s.rows) {
            val end=row.endedAtMs?.let { "${(it-row.startedAtMs).coerceAtLeast(0)/60000} мин · ${row.endReason ?: "н/д"}" } ?: "Нет завершения"
            list.addView(LegionUiSupport.actionTile(this,row.appLabel,
                "${row.packageName} · ${date.format(Date(row.startedAtMs))}\n$end · ${row.id.take(8)}") {
                if(!s.busy)vm.select(row.id)
            },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8.dp })
        }
        val d=s.detail
        val newlySelected=d!=null && d!==lastDetail
        lastDetail=d
        detail.text=if(d==null)"Выберите завершённую запись." else buildString {
            append(GameSessionAnalysis.render(GameSessionAnalysis.summarize(d.header,d.points)))
            append("\n\nСобытий в архиве: ").append(d.events.size)
            if(d.sampleLimited || d.eventLimited)append("\nДостигнут лимит чтения: архив может быть неполным.")
            append("\nПоследние 10 событий из доступного окна:\n")
            d.events.takeLast(10).forEach { append(date.format(Date(it.timeMs))).append(" · ").append(it.kind)
                .append(" · ").append(it.details).append('\n') }
        }
        if(newlySelected) detail.post {
            detail.requestRectangleOnScreen(android.graphics.Rect(0,0,detail.width,
                minOf(detail.height,96.dp)),true)
        }
    }
}
