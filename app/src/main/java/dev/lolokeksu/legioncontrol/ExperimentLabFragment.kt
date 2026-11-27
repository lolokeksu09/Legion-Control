package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * All experiment and annotation edits happen only at the user's direction.
 * The recorded evidence is immutable and is never replaced with a simulated benchmark.
 */
class ExperimentLabFragment : Fragment() {
    companion object {
        private const val EXPORT_CSV=1901
    }
    private lateinit var vm: ExperimentLabViewModel
    private lateinit var appInput:EditText
    private lateinit var experimentName:EditText
    private lateinit var hypothesis:EditText
    private lateinit var labelA:EditText
    private lateinit var labelB:EditText
    private lateinit var tagInput:EditText
    private lateinit var noteInput:EditText
    private lateinit var status:TextView
    private lateinit var sessionContext:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val layout=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,28.dp,18.dp,40.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(text:String) {
            layout.addView(TextView(requireContext()).apply {
                this.text=text
                textSize=19f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0,18.dp,0,12.dp)
            })
        }
        fun subtitle(text:String) {
            layout.addView(TextView(requireContext()).apply {
                this.text=text
                textSize=13f
                setTextColor(0xFFB4B4B4.toInt())
                setPadding(0,8.dp,0,12.dp)
            })
        }
        fun input(hintLabel:String,defaultText:String=""):EditText=
            EditText(requireContext()).apply {
                hint=hintLabel
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setText(defaultText)
                layout.addView(this)
            }
        fun action(text:String,featureId:String?=null,click:()->Unit) {
            layout.addView(Button(requireContext()).apply {
                if(featureId!=null) FeatureReveal.bind(this,featureId)
                this.text=text
                minHeight=48.dp
                setOnClickListener { click() }
            })
        }
        title("LEGION · EXPERIMENT LAB")
        subtitle("Реальная история игровых сессий, заметки, A/B-сравнение условий и экспорт. "+
            "Для любых Android-приложений. Никакого Root/Shizuku, FPS игры или фиктивного benchmark.")
        title("1 · История и метки")
        appInput=input("Пакет приложения (необязательно для списка)")
        action("Выбрать приложение из истории") { pickPackage() }
        sessionContext = TextView(requireContext()).apply {
            id = R.id.experiment_selected_session
            setTextColor(LegionUiSupport.MUTED)
            textSize = 14f
            setPadding(0, 8.dp, 0, 8.dp)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "Сессия не выбрана."
        }
        layout.addView(sessionContext)
        action("Выбрать записанную сессию") { pickSession() }
        tagInput=input("Тег сессии · до 40 символов")
        noteInput=EditText(requireContext()).apply {
            hint="Заметка к реальной сессии · до 1000 символов"
            minLines=3;maxLines=5
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        layout.addView(noteInput)
        action("Сохранить тег и заметку") { saveAnnotation() }
        action("Сравнить две последние сессии этого приложения") { compareLastTwo() }
        action("Качество и диагностика выбранной сессии") { showEvidence() }
        title("2 · A/B эксперимент")
        subtitle("Один эксперимент объединяет сессии ОДНОГО приложения. "+
            "Подписи A/B обозначают условия, которые задаёте вы, а не переключение системных настроек.")
        experimentName=input("Название эксперимента · до 100")
        hypothesis=input("Гипотеза/цель · до 500")
        labelA=input("Условие A, например Wi-Fi 5 ГГц","A")
        labelB=input("Условие B, например мобильная сеть","B")
        action("Создать эксперимент", "experiment.create") { create() }
        action("Выбрать сохранённый эксперимент") { pickExperiment() }
        action("Изменить название и условия выбранного") { updateExperiment() }
        action("Назначить выбранную сессию → A") { assign("A") }
        action("Назначить выбранную сессию → B") { assign("B") }
        action("Убрать выбранную сессию из эксперимента") { unassign() }
        action("Показать состав A/B и метки сессий") { showTrialRoster() }
        action("Показать сравнение A/B", "experiment.compare") { compare() }
        action("Экспортировать A/B данные CSV", "experiment.csv") { exportCsv() }
        action("Поделиться сравнением текстом") { shareComparison() }
        action("Удалить эксперимент (с подтверждением)") { confirmDelete() }
        subtitle("Пики температуры относятся к БАТАРЕЕ. Заряд оценивается в п.п./час, "+
            "если измерений достаточно. Никакое сравнение не доказывает, что настройка повысила FPS.")
        status=TextView(requireContext()).apply {
            setTextColor(Color.WHITE)
            textSize=14f
            setLineSpacing(4f,1f)
            setPadding(0,18.dp,0,32.dp)
            text="Выберите приложение и сохранённую сессию. Измерения не будут изменены."
        }
        LegionUiSupport.reportSurface(status)
        layout.addView(status)
        return ScrollView(requireContext()).apply { addView(layout) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.EXPERIMENT)
        vm=ViewModelProvider(this)[ExperimentLabViewModel::class.java]
        vm.state.observe(viewLifecycleOwner) { state ->
            display(state.message)
            state.packages?.let { vm.consume(state.nonce); showPackagePicker(it) }
            state.sessions?.let { vm.consume(state.nonce); showSessionPicker(it) }
            state.experiments?.let { vm.consume(state.nonce); showExperimentPicker(it) }
            state.share?.let { text ->
                vm.consume(state.nonce)
                runCatching {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/plain";putExtra(Intent.EXTRA_TEXT,text)
                    },"Поделиться анализом"))
                }.onFailure { display("Не удалось открыть меню отправки.") }
            }
        }
        if(savedInstanceState!=null) {
            appInput.setText(savedInstanceState.getString("package").orEmpty())
            experimentName.setText(savedInstanceState.getString("name").orEmpty())
            hypothesis.setText(savedInstanceState.getString("hypothesis").orEmpty())
            labelA.setText(savedInstanceState.getString("a","A"))
            labelB.setText(savedInstanceState.getString("b","B"))
            tagInput.setText(savedInstanceState.getString("tag").orEmpty())
            noteInput.setText(savedInstanceState.getString("note").orEmpty())
        } else {
            arguments?.getString(ExperimentLabActivity.EXTRA_PACKAGE)?.takeIf {
                SmartProfilesRepository.validatePackage(it)
            }?.let { appInput.setText(it) }
        }

        appInput.doAfterTextChanged {
            vm.clearSessionForPackage(currentPackage())
        }
        vm.selection.observe(viewLifecycleOwner) { selected ->
            sessionContext.text = selected?.header?.let { header ->
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(header.startedAtMs))
                "${header.appLabel} · $time · ${header.id.take(8)}\n${selected.message}"
            } ?: selected?.message ?: "Сессия не выбрана."
            if (selected?.fillDraft == true) {
                tagInput.setText(selected.annotation?.tag.orEmpty())
                noteInput.setText(selected.annotation?.note.orEmpty())
                vm.finishFillingDraft()
            }
        }
        val id = if (savedInstanceState == null) arguments?.getString(ExperimentLabActivity.EXTRA_SESSION)
            else savedInstanceState.getString("selected_session")
        val draftReady = savedInstanceState?.getBoolean("annotation_draft_ready") == true
        vm.initializeSession(currentPackage(), id, restoreDraft = draftReady)
    }

    private fun display(t:String) { if(::status.isInitialized) status.text=t }

    private fun currentPackage():String? =
        appInput.text.toString().trim().takeIf { SmartProfilesRepository.validatePackage(it) }

    private fun pickPackage() = vm.pickPackages()

    private fun showPackagePicker(names:List<Pair<String,String>>) {
        if(names.isEmpty()) { display("Пока нет записанных сессий.");return }
        LegionDialogs.builder(requireContext()).setTitle("Приложения из истории")
            .setItems(names.map { it.second+"\n"+it.first }.toTypedArray()) { _,i ->
                appInput.setText(names[i].first)
                display("Фильтр: "+names[i].second)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun pickSession() {
        val filter=currentPackage()
        if(appInput.text.toString().trim().isNotEmpty() && filter==null) {
            display("Некорректное имя пакета Android.");return
        }
        vm.pickSessions(filter)
    }

    private fun showSessionPicker(sessions:List<GameSessionHeader>) {
        if(sessions.isEmpty()) { display("Не найдено записанных сессий.");return }
        LegionDialogs.builder(requireContext()).setTitle("Выберите сессию")
            .setItems(sessions.map { row ->
                row.appLabel+" · "+DateFormat.getDateTimeInstance().format(Date(row.startedAtMs))+
                    "\n"+row.packageName+" · "+(row.endReason ?: "не завершена")
            }.toTypedArray()) { _,index ->
                val session=sessions[index]
                appInput.setText(session.packageName)
                tagInput.setText("")
                noteInput.setText("")
                vm.selectSession(session.packageName, session.id)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun saveAnnotation() {
        val id=vm.selectedSession ?: return display("Сначала выберите сессию.")
        val tag=tagInput.text.toString().trim()
        val note=noteInput.text.toString().trim()
        if(tag.length>40 || note.length>1000) return display("Тег до 40, заметка до 1000 символов.")
        vm.saveAnnotation(id,tag,note)
    }

    private fun showEvidence() {
        val id=vm.selectedSession ?: return display("Сначала выберите сессию.")
        vm.showEvidence(id)
    }

    private fun compareLastTwo() {
        val pkg=currentPackage() ?: return display("Выберите пакет приложения для сравнения.")
        vm.compareLastTwo(pkg)
    }

    private fun showTrialRoster() {
        val id=vm.selectedExperiment ?: return display("Выберите эксперимент.")
        vm.roster(id)
    }

    private fun create() {
        val pkg=currentPackage() ?: return display("Выберите корректный пакет приложения.")
        val name=experimentName.text.toString().trim()
        val h=hypothesis.text.toString().trim()
        val a=labelA.text.toString().trim()
        val b=labelB.text.toString().trim()
        if(name.isBlank()||name.length>100||h.length>500||
            a.isBlank()||b.isBlank()||a.length>60||b.length>60)
            return display("Проверьте название, цель и подписи двух условий.")
        vm.createExperiment(pkg,name,h,a,b)
    }

    private fun pickExperiment()=vm.pickExperiments()

    private fun showExperimentPicker(found:List<SessionExperiment>) {
        if(found.isEmpty()) { display("Пока нет экспериментов.");return }
        LegionDialogs.builder(requireContext()).setTitle("Эксперименты")
            .setItems(found.map { it.name+"\n"+it.packageName }.toTypedArray()) { _,i ->
                val e=found[i]
                vm.selectExperiment(e.id)
                appInput.setText(e.packageName)
                experimentName.setText(e.name)
                hypothesis.setText(e.hypothesis)
                labelA.setText(e.labelA)
                labelB.setText(e.labelB)
                display("Эксперимент: "+e.name+"\nID: "+e.id)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun updateExperiment() {
        val id=vm.selectedExperiment ?: return display("Сначала выберите эксперимент.")
        val name=experimentName.text.toString().trim()
        val h=hypothesis.text.toString().trim()
        val a=labelA.text.toString().trim()
        val b=labelB.text.toString().trim()
        if(name.isBlank()||name.length>100||h.length>500||
            a.isBlank()||b.isBlank()||a.length>60||b.length>60)
            return display("Недопустимая длина названия или условий.")
        vm.updateExperiment(id,name,h,a,b)
    }

    private fun assign(arm:String) {
        val experiment=vm.selectedExperiment ?: return display("Выберите эксперимент.")
        val session=vm.selectedSession ?: return display("Выберите реальную сессию.")
        vm.assign(experiment,session,arm)
    }

    private fun unassign() {
        val experiment=vm.selectedExperiment ?: return display("Выберите эксперимент.")
        val session=vm.selectedSession ?: return display("Выберите сессию.")
        vm.unassign(experiment,session)
    }

    private fun compare() {
        val id=vm.selectedExperiment ?: return display("Выберите эксперимент.")
        vm.compare(id)
    }

    private fun exportCsv() {
        if(vm.selectedExperiment==null) return display("Выберите эксперимент.")
        val request=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/csv"
            putExtra(Intent.EXTRA_TITLE,"legion-experiment-alpha19.csv")
        }
        runCatching { startActivityForResult(request,EXPORT_CSV) }
            .onFailure { display("Не удалось выбрать файл: "+it.javaClass.simpleName) }
    }

    private fun shareComparison() {
        val id=vm.selectedExperiment ?: return display("Выберите эксперимент.")
        vm.share(id)
    }

    private fun confirmDelete() {
        val id=vm.selectedExperiment ?: return display("Выберите эксперимент.")
        LegionDialogs.builder(requireContext()).setTitle("Удалить эксперимент?")
            .setMessage("Будут удалены его A/B назначения, но НЕ оригинальные игровые сессии, "+
                "измерения, заметки и профили.")
            .setDestructive().setPositiveButton("Удалить") { _,_ ->
                vm.delete(id)
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=EXPORT_CSV || resultCode!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        val id=vm.selectedExperiment ?: return
        vm.exportCsv(id,uri)
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("package",appInput.text.toString())
        outState.putString("selected_session",vm.requestedSessionId)
        val selection = vm.selection.value
        outState.putBoolean("annotation_draft_ready", selection?.header != null && !selection.fillDraft)
        outState.putString("name",experimentName.text.toString())
        outState.putString("hypothesis",hypothesis.text.toString())
        outState.putString("a",labelA.text.toString())
        outState.putString("b",labelB.text.toString())
        outState.putString("tag",tagInput.text.toString())
        outState.putString("note",noteInput.text.toString())
        super.onSaveInstanceState(outState)
    }
}
