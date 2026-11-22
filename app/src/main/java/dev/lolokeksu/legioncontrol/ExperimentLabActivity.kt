package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
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
class ExperimentLabActivity : Activity() {
    companion object {
        private const val EXPORT_CSV=1901
    }
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var appInput:EditText
    private lateinit var experimentName:EditText
    private lateinit var hypothesis:EditText
    private lateinit var labelA:EditText
    private lateinit var labelB:EditText
    private lateinit var tagInput:EditText
    private lateinit var noteInput:EditText
    private lateinit var status:TextView
    private var selectedExperiment:String?=null
    private var selectedSession:String?=null
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,28.dp,18.dp,40.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(text:String) {
            layout.addView(TextView(this).apply {
                this.text=text
                textSize=19f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0,18.dp,0,12.dp)
            })
        }
        fun subtitle(text:String) {
            layout.addView(TextView(this).apply {
                this.text=text
                textSize=13f
                setTextColor(0xFFB4B4B4.toInt())
                setPadding(0,8.dp,0,12.dp)
            })
        }
        fun input(hintLabel:String,defaultText:String=""):EditText=
            EditText(this).apply {
                hint=hintLabel
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setText(defaultText)
                layout.addView(this)
            }
        fun action(text:String,click:()->Unit) {
            layout.addView(Button(this).apply {
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
        action("Выбрать записанную сессию") { pickSession() }
        tagInput=input("Тег сессии · до 40 символов")
        noteInput=EditText(this).apply {
            hint="Заметка к реальной сессии · до 1000 символов"
            minLines=3;maxLines=5
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        layout.addView(noteInput)
        action("Сохранить тег и заметку") { saveAnnotation() }
        action("Качество и диагностика выбранной сессии") { showEvidence() }
        title("2 · A/B эксперимент")
        subtitle("Один эксперимент объединяет сессии ОДНОГО приложения. "+
            "Подписи A/B обозначают условия, которые задаёте вы, а не переключение системных настроек.")
        experimentName=input("Название эксперимента · до 100")
        hypothesis=input("Гипотеза/цель · до 500")
        labelA=input("Условие A, например Wi-Fi 5 ГГц","A")
        labelB=input("Условие B, например мобильная сеть","B")
        action("Создать эксперимент") { create() }
        action("Выбрать сохранённый эксперимент") { pickExperiment() }
        action("Изменить название и условия выбранного") { updateExperiment() }
        action("Назначить выбранную сессию → A") { assign("A") }
        action("Назначить выбранную сессию → B") { assign("B") }
        action("Убрать выбранную сессию из эксперимента") { unassign() }
        action("Показать сравнение A/B") { compare() }
        action("Экспортировать A/B данные CSV") { exportCsv() }
        action("Поделиться сравнением текстом") { shareComparison() }
        action("Удалить эксперимент (с подтверждением)") { confirmDelete() }
        subtitle("Пики температуры относятся к БАТАРЕЕ. Заряд оценивается в п.п./час, "+
            "если измерений достаточно. Никакое сравнение не доказывает, что настройка повысила FPS.")
        status=TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize=14f
            setLineSpacing(4f,1f)
            setPadding(0,18.dp,0,32.dp)
            text="Выберите приложение и сохранённую сессию. Измерения не будут изменены."
        }
        layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })
        intent.getStringExtra("experiment_package")?.takeIf {
            SmartProfilesRepository.validatePackage(it)
        }?.let { appInput.setText(it) }
    }

    private fun display(t:String) { if(::status.isInitialized) status.text=t }

    private fun work(task:()->String) {
        display("Чтение локальных данных…")
        worker.execute {
            val message=runCatching { task() }.getOrElse {
                "Ошибка: "+it.javaClass.simpleName+" · "+it.message.orEmpty().take(150)
            }
            runOnUiThread { if(!isFinishing && !isDestroyed) display(message) }
        }
    }

    private fun currentPackage():String? =
        appInput.text.toString().trim().takeIf { SmartProfilesRepository.validatePackage(it) }

    private fun pickPackage() = work {
        ExperimentRepository(applicationContext).use { repo ->
            val names=repo.recentSessions(limit=100).map { it.packageName to it.appLabel }
                .distinctBy { it.first }
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    if(names.isEmpty()) display("Пока нет записанных сессий.")
                    else AlertDialog.Builder(this).setTitle("Приложения из истории")
                        .setItems(names.map { it.second+"\n"+it.first }.toTypedArray()) { _,index ->
                            appInput.setText(names[index].first)
                            display("Фильтр: "+names[index].second)
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "Приложений в последних 100 сессиях: ${names.size}"
        }
    }

    private fun pickSession() = work {
        val filter=currentPackage()
        val raw=appInput.text.toString().trim()
        if(raw.isNotEmpty() && filter==null) return@work "Некорректное имя пакета Android."
        ExperimentRepository(applicationContext).use { repo ->
            val sessions=repo.recentSessions(filter,60)
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    if(sessions.isEmpty()) display("Не найдено записанных сессий.")
                    else AlertDialog.Builder(this).setTitle("Выберите сессию")
                        .setItems(sessions.map { s ->
                            s.appLabel+" · "+DateFormat.getDateTimeInstance().format(Date(s.startedAtMs))+
                            "\n"+s.packageName+" · "+(s.endReason ?: "не завершена")
                        }.toTypedArray()) { _,index ->
                            val s=sessions[index]
                            selectedSession=s.id
                            appInput.setText(s.packageName)
                            tagInput.setText("")
                            noteInput.setText("")
                            work {
                                ExperimentRepository(applicationContext).use { source ->
                                    val annotation=source.getAnnotation(s.id)
                                    runOnUiThread {
                                        if(!isFinishing && !isDestroyed && selectedSession==s.id) {
                                            tagInput.setText(annotation?.tag.orEmpty())
                                            noteInput.setText(annotation?.note.orEmpty())
                                        }
                                    }
                                }
                                "Сессия выбрана: "+s.appLabel+" / "+s.id
                            }
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "Найдено сессий: ${sessions.size}"
        }
    }

    private fun saveAnnotation() {
        val id=selectedSession ?: return display("Сначала выберите сессию.")
        val tag=tagInput.text.toString().trim()
        val note=noteInput.text.toString().trim()
        if(tag.length>40 || note.length>1000) return display("Тег до 40, заметка до 1000 символов.")
        work {
            ExperimentRepository(applicationContext).use { it.annotate(id,tag,note) }
            "Метка и заметка сохранены для $id. Исходные измерения не изменены."
        }
    }

    private fun showEvidence() {
        val id=selectedSession ?: return display("Сначала выберите сессию.")
        work {
            ExperimentRepository(applicationContext).use { repo ->
                val annotation=repo.getAnnotation(id)
                ExperimentAnalytics.formatEvidence(repo.evidence(id))+
                    "\n\nМетка: "+(annotation?.tag?.ifBlank { "—" } ?: "—")+
                    "\nЗаметка: "+(annotation?.note?.ifBlank { "—" } ?: "—")
            }
        }
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
        work {
            val id=ExperimentRepository(applicationContext).use { it.create(pkg,name,h,a,b) }
            selectedExperiment=id
            "Эксперимент создан: $name\n$id\nНазначьте реальные сессии для A и B."
        }
    }

    private fun pickExperiment() = work {
        ExperimentRepository(applicationContext).use { repo ->
            val found=repo.experiments()
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    if(found.isEmpty()) display("Пока нет экспериментов.")
                    else AlertDialog.Builder(this).setTitle("Эксперименты")
                        .setItems(found.map { it.name+"\n"+it.packageName }.toTypedArray()) { _,index ->
                            val e=found[index]
                            selectedExperiment=e.id
                            appInput.setText(e.packageName)
                            experimentName.setText(e.name)
                            hypothesis.setText(e.hypothesis)
                            labelA.setText(e.labelA)
                            labelB.setText(e.labelB)
                            display("Эксперимент: "+e.name+"\nID: "+e.id)
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "Экспериментов: ${found.size}"
        }
    }

    private fun updateExperiment() {
        val id=selectedExperiment ?: return display("Сначала выберите эксперимент.")
        val name=experimentName.text.toString().trim()
        val h=hypothesis.text.toString().trim()
        val a=labelA.text.toString().trim()
        val b=labelB.text.toString().trim()
        if(name.isBlank()||name.length>100||h.length>500||
            a.isBlank()||b.isBlank()||a.length>60||b.length>60)
            return display("Недопустимая длина названия или условий.")
        work {
            val updated=ExperimentRepository(applicationContext).use { it.update(id,name,h,a,b) }
            if(updated) "Эксперимент обновлён." else "Эксперимент не найден."
        }
    }

    private fun assign(arm:String) {
        val experiment=selectedExperiment ?: return display("Выберите эксперимент.")
        val session=selectedSession ?: return display("Выберите реальную сессию.")
        work {
            ExperimentRepository(applicationContext).use { it.assign(experiment,session,arm) }
            "Сессия $session назначена условию $arm. Сохранённые замеры не изменены."
        }
    }

    private fun unassign() {
        val experiment=selectedExperiment ?: return display("Выберите эксперимент.")
        val session=selectedSession ?: return display("Выберите сессию.")
        work {
            val deleted=ExperimentRepository(applicationContext).use { it.unassign(experiment,session) }
            if(deleted) "Сессия исключена из эксперимента." else "Назначение отсутствовало."
        }
    }

    private fun compare() {
        val id=selectedExperiment ?: return display("Выберите эксперимент.")
        work { ExperimentRepository(applicationContext).use { it.report(id) } }
    }

    private fun exportCsv() {
        if(selectedExperiment==null) return display("Выберите эксперимент.")
        val request=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/csv"
            putExtra(Intent.EXTRA_TITLE,"legion-experiment-alpha19.csv")
        }
        runCatching { startActivityForResult(request,EXPORT_CSV) }
            .onFailure { display("Не удалось выбрать файл: "+it.javaClass.simpleName) }
    }

    private fun shareComparison() {
        val id=selectedExperiment ?: return display("Выберите эксперимент.")
        work {
            val report=ExperimentRepository(applicationContext).use { it.report(id) }
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/plain"
                        putExtra(Intent.EXTRA_TEXT,report)
                    },"Поделиться анализом"))
                }
            }
            report
        }
    }

    private fun confirmDelete() {
        val id=selectedExperiment ?: return display("Выберите эксперимент.")
        AlertDialog.Builder(this).setTitle("Удалить эксперимент?")
            .setMessage("Будут удалены его A/B назначения, но НЕ оригинальные игровые сессии, "+
                "измерения, заметки и профили.")
            .setPositiveButton("Удалить") { _,_ ->
                work {
                    val ok=ExperimentRepository(applicationContext).use { it.delete(id) }
                    if(ok) {
                        selectedExperiment=null
                        "Эксперимент удалён; история не затронута."
                    } else "Эксперимент не найден."
                }
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=EXPORT_CSV || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        val id=selectedExperiment ?: return
        work {
            ExperimentRepository(applicationContext).use { repo ->
                (contentResolver.openOutputStream(uri,"w") ?: error("Cannot open export")).use { stream ->
                    OutputStreamWriter(stream,Charsets.UTF_8).use { writer ->
                        repo.exportCsv(id,writer)
                    }
                }
            }
            "CSV сохранён в выбранный документ. Экспорт выполнен только по вашему действию."
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
