package dev.lolokeksu.legioncontrol

import android.app.AlertDialog
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
    private var pendingExport:LibraryDetail?=null
    private val Int.dp get()=(this*resources.displayMetrics.density+.5f).toInt()
    private val document=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selected=pendingExport
        pendingExport=null
        if(uri!=null && selected!=null && ::vm.isInitialized)vm.export(uri,selected)
        else if(uri!=null && ::status.isInitialized)status.text="Выбор изменился. Повторите экспорт."
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
        fun button(title:String,action:()->Unit)=Button(this).apply {
            text=title;LegionUiSupport.styleAction(this);setOnClickListener { action() };root.addView(this)
        }
        label("SESSION LIBRARY",22f).apply { setTypeface(Typeface.DEFAULT,Typeface.BOLD);isAccessibilityHeading=true }
        label("Локальная история: фильтры, страницы и отдельный архив записи. Данные не удаляются.",13f,LegionUiSupport.MUTED)
        status=label("Загрузка истории…",14f,LegionUiSupport.ACCENT)
        root.addView(LegionUiSupport.sectionLabel(this,"ФИЛЬТРЫ"))
        filters=label("")
        game=button("Фильтр по игре") {
            val s=vm.state.value ?: return@button
            val packages=(s.packages+listOfNotNull(s.filter.packageName)).distinct()
            AlertDialog.Builder(this).setTitle("Игра")
                .setItems((listOf("Все игры")+packages).toTypedArray()) { _,i ->
                    vm.refresh(s.filter.copy(packageName=packages.getOrNull(i-1)))
                }.setNegativeButton("Отмена",null).show()
        }
        period=button("Фильтр по периоду") {
            val s=vm.state.value ?: return@button
            val options=LibraryPeriod.entries
            AlertDialog.Builder(this).setTitle("Период").setItems(options.map { it.label }.toTypedArray()) { _,i->
                vm.refresh(s.filter.copy(period=options[i]))
            }.setNegativeButton("Отмена",null).show()
        }
        stateButton=button("Фильтр по завершению") {
            val s=vm.state.value ?: return@button
            val options=LibraryStatus.entries
            AlertDialog.Builder(this).setTitle("Состояние записи").setItems(options.map { it.label }.toTypedArray()) { _,i->
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
        detail=label("Выберите завершённую запись.").apply { setTextIsSelectable(true) }
        export=button("ЭКСПОРТ ВЫБРАННОЙ СЕССИИ ZIP") {
            val selected=vm.state.value?.detail ?: return@button
            pendingExport=selected
            runCatching { document.launch("legion-session-${selected.header.id.take(8)}.zip") }
                .onFailure { pendingExport=null;status.text="Не удалось открыть выбор документа." }
        }
        LegionUiSupport.setModuleActivityContent(this,ScrollView(this).apply { addView(root) },"БИБЛИОТЕКА ИГРОВЫХ СЕССИЙ")
        vm.state.observe(this,::render)
        vm.initialize(intent.getStringExtra(EXTRA_PACKAGE))
        FeatureReveal.fromActivity(this,root,FeatureModule.LIBRARY)
    }
    private fun render(s:LibraryState) {
        status.text=s.message
        filters.text="${s.filter.packageName ?: "Все игры"} · ${s.filter.period.label} · ${s.filter.status.label}\nСтраница ${s.previous.size+1}"
        game.isEnabled=!s.busy;period.isEnabled=!s.busy;stateButton.isEnabled=!s.busy;refresh.isEnabled=!s.busy
        previous.isEnabled=!s.busy && s.previous.isNotEmpty()
        next.isEnabled=!s.busy && s.next!=null && s.previous.size<499
        export.isEnabled=!s.busy && s.detail!=null
        list.removeAllViews()
        if(s.rows.isEmpty())list.addView(TextView(this).apply {
            text="Записей нет.";setTextColor(LegionUiSupport.MUTED);setPadding(0,8.dp,0,8.dp)
        })
        val date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        for(row in s.rows) {
            val end=row.endedAtMs?.let { "${(it-row.startedAtMs).coerceAtLeast(0)/60000} мин · ${row.endReason ?: "н/д"}" } ?: "Нет завершения"
            list.addView(LegionUiSupport.actionTile(this,row.appLabel,
                "${row.packageName} · ${date.format(Date(row.startedAtMs))}\n$end · ${row.id.take(8)}") {
                if(!s.busy)vm.select(row.id)
            },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8.dp })
        }
        val d=s.detail
        detail.text=if(d==null)"Выберите завершённую запись." else buildString {
            append(GameSessionAnalysis.render(GameSessionAnalysis.summarize(d.header,d.points)))
            append("\n\nСобытий в архиве: ").append(d.events.size)
            if(d.sampleLimited || d.eventLimited)append("\nДостигнут лимит чтения: архив может быть неполным.")
            append("\nПоследние 10 событий из доступного окна:\n")
            d.events.takeLast(10).forEach { append(date.format(Date(it.timeMs))).append(" · ").append(it.kind)
                .append(" · ").append(it.details).append('\n') }
        }
    }
}
