package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** App library. Every app action requires a tap and uses documented Android APIs. */
class ApplicationCenterActivity:Activity() {
    companion object { private const val EXPORT_CSV=2101 }
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var visible=false
    private lateinit var packageText:EditText
    private lateinit var aliasText:EditText
    private lateinit var categoryText:EditText
    private lateinit var noteText:EditText
    private lateinit var favorite:CheckBox
    private lateinit var output:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,28.dp,18.dp,32.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(value:String) {
            root.addView(TextView(this).apply {
                text=value; textSize=20f; setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD)
                setPadding(0,12.dp,0,12.dp)
            })
        }
        fun note(value:String) {
            root.addView(TextView(this).apply {
                text=value; textSize=13f; setTextColor(0xFFAAAAAA.toInt())
                setPadding(0,6.dp,0,12.dp)
            })
        }
        fun edit(hintLabel:String,multiLine:Boolean=false):EditText=
            EditText(this).apply {
                hint=hintLabel
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setSingleLine(!multiLine)
                if(multiLine) { minLines=2;maxLines=5 }
                root.addView(this)
            }
        fun button(label:String,action:()->Unit) {
            root.addView(Button(this).apply {
                text=label;isAllCaps=false;minHeight=48.dp
                setOnClickListener { action() }
            })
        }
        title("APPLICATION CONTROL CENTER")
        note("Legion Control alpha.21 · библиотека игр и приложений Android 13+. "+
            "Обнаруживаются только видимые Android приложения. "+
            "Ни Root/Shizuku, ни изменения чужих разрешений не используются.")
        button("Выбрать видимое установленное приложение") { chooseApp() }
        button("Найти приложение по названию / пакету") { searchApp() }
        button("Моя библиотека / избранное") { chooseTracked() }
        button("Только избранные приложения") { chooseTracked(onlyFavorites=true) }
        packageText=edit("Имя пакета · например com.example.game")
        aliasText=edit("Название в библиотеке · до 100 символов")
        categoryText=edit("Категория · до 40 символов")
        noteText=edit("Локальная заметка · до 1000 символов",true)
        favorite=CheckBox(this).apply {
            text="В избранное"
            setTextColor(Color.WHITE)
        }
        root.addView(favorite)
        button("СОХРАНИТЬ В БИБЛИОТЕКУ") { save() }
        button("Проверить версию и состояние выбранного") { scanOne() }
        button("Проверить изменения всех отслеживаемых приложений") { scanAll() }
        button("Карточка приложения и видимые разрешения") { inspect() }
        button("Последние события обновлений и видимости") { showEvents() }
        button("Удалить из библиотеки (с подтверждением)") { confirmRemove() }
        note("Проверка вручную сравнивает видимую версию и дату обновления с "+
            "предыдущей записью. «Пакет недоступен» не означает, что он удалён.")
        title("Использование · Android API")
        button("Использование приложения за 24 часа") { usage(1) }
        button("Использование приложения за 7 дней") { usage(7) }
        button("Разрешить Usage Access в настройках") {
            openSystem(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        note("Usage Access необязателен. Android даёт агрегированные данные, "+
            "а не статистику матчей и не время работы CPU/GPU.")
        title("Системное управление · официальные экраны")
        button("ЗАПУСТИТЬ выбранное приложение") { launchApp() }
        button("Настройки выбранного приложения") {
            selectedPackage()?.let { openSystem(ApplicationInventory.appInfo(this,it)) }
        }
        button("Настройки уведомлений выбранного приложения") {
            selectedPackage()?.let { openSystem(ApplicationInventory.notifications(this,it)) }
        }
        button("Оптимизация батареи · системный список") {
            openSystem(ApplicationInventory.batterySettings())
        }
        button("Открыть Smart Profiles") {
            startActivity(Intent(this,SmartProfilesActivity::class.java).apply {
                selectedPackage()?.let { putExtra("app_center_package",it) }
            })
        }
        button("Открыть Experiment Lab для этого пакета") {
            startActivity(Intent(this,ExperimentLabActivity::class.java).apply {
                selectedPackage()?.let { putExtra("experiment_package",it) }
            })
        }
        title("Экспорт локальных данных")
        button("СОХРАНИТЬ библиотеку и события в CSV") { exportCsv() }
        note("CSV сохраняется через системное окно выбора файла. "+
            "Примечания могут быть личными — проверьте их перед публикацией. "+
            "Legion Control ничего не выгружает самостоятельно.")
        output=TextView(this).apply {
            setTextColor(Color.WHITE);textSize=14f
            setLineSpacing(4f,1f)
            setTextIsSelectable(true)
            setPadding(0,16.dp,0,30.dp)
            text="Выберите приложение. Все проверки запускаются только по нажатию."
        }
        root.addView(output)
        setContentView(ScrollView(this).apply { addView(root) })
        intent.getStringExtra("app_center_package")?.takeIf {
            ApplicationInventory.isPackageName(it)
        }?.let { packageText.setText(it) }
    }

    private fun show(message:String) { if(::output.isInitialized) output.text=message }

    private fun selectedPackage():String? {
        val pkg=packageText.text.toString().trim()
        if(!ApplicationInventory.isPackageName(pkg)) {
            show("Укажите корректное Android package name, например com.example.game.")
            return null
        }
        return pkg
    }

    private fun work(action:()->String) {
        show("Обработка локальных данных…")
        worker.execute {
            val result=runCatching { action() }.getOrElse {
                "Ошибка: "+it.javaClass.simpleName+" · "+it.message.orEmpty().take(160)
            }
            runOnUiThread { if(!isFinishing && !isDestroyed) show(result) }
        }
    }

    private fun chooseApp() { pickApp(null) }

    private fun searchApp() {
        val input=EditText(this).apply {
            hint="Поиск в доступном списке приложений"
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Поиск").setView(input)
            .setPositiveButton("Найти") { _,_ -> pickApp(input.text.toString().trim()) }
            .setNegativeButton("Отмена",null).show()
    }

    private fun pickApp(filter:String?) = work {
        val found=ApplicationInventory.launchable(applicationContext).filter { item ->
            filter.isNullOrBlank() ||
                item.label.contains(filter,ignoreCase=true) ||
                item.packageName.contains(filter,ignoreCase=true)
        }
        runOnUiThread {
            if(!isFinishing && !isDestroyed) {
                if(found.isEmpty()) show("Android не вернул видимых приложений по запросу.")
                else AlertDialog.Builder(this).setTitle("Приложения · ${found.size}")
                    .setItems(found.map { it.label+"\n"+it.packageName }.toTypedArray()) { _,index ->
                        packageText.setText(found[index].packageName)
                        aliasText.setText(found[index].label)
                        categoryText.setText("")
                        noteText.setText("")
                        favorite.isChecked=false
                        show("Выбрано: "+found[index].label+
                            "\nМожете добавить его в библиотеку.")
                    }.setNegativeButton("Отмена",null).show()
            }
        }
        "Из доступных Launcher API найдено: ${found.size}"
    }

    private fun chooseTracked(onlyFavorites:Boolean=false) = work {
        ApplicationCenterRepository(applicationContext).use { repository ->
            val entries=repository.list().filter { !onlyFavorites || it.favorite }
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    if(entries.isEmpty()) show("Библиотека пуста — сохраните приложение.")
                    else AlertDialog.Builder(this).setTitle("Моя библиотека · ${entries.size}")
                        .setItems(entries.map {
                            (if(it.favorite)"★ " else "")+
                                it.alias.ifBlank { it.packageName }+
                                (if(it.category.isBlank())"" else " · "+it.category)+
                                "\n"+it.packageName
                        }.toTypedArray()) { _,index ->
                            val item=entries[index]
                            packageText.setText(item.packageName)
                            aliasText.setText(item.alias)
                            categoryText.setText(item.category)
                            noteText.setText(item.note)
                            favorite.isChecked=item.favorite
                            show("Выбрано: "+item.packageName+
                                "\nНаблюдение: "+item.lastSeenStatus)
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "Отслеживаемых: ${entries.size}"
        }
    }

    private fun save() {
        val pkg=selectedPackage() ?: return
        val alias=aliasText.text.toString().trim()
        val category=categoryText.text.toString().trim()
        val note=noteText.text.toString().trim()
        val selected=favorite.isChecked
        if(alias.length>100||category.length>40||note.length>1000) {
            show("Лимиты: название 100, категория 40, заметка 1000 символов.")
            return
        }
        work {
            ApplicationCenterRepository(applicationContext).use { repository ->
                repository.save(pkg,alias,category,note,selected)
            }
            "Сохранено: $pkg · избранное: $selected. "+
                "Запустите отдельную проверку версии, когда она нужна."
        }
    }

    private fun scanOne() {
        val pkg=selectedPackage() ?: return
        work {
            val observation=ApplicationInventory.inspect(applicationContext,pkg)
            ApplicationCenterRepository(applicationContext).use { repo ->
                val result=repo.audit(pkg,observation)
                "Проверено: $pkg\n"+result.description+"\n"+
                    "Событие: "+(result.event?.name ?: "нет")+
                    "\n\n"+observation.report()
            }
        }
    }

    private fun scanAll() = work {
        val tracked=ApplicationCenterRepository(applicationContext).use { repo ->
            repo.list()
        }
        if(tracked.isEmpty()) return@work "Библиотека пуста — нечего проверять."
        var changed=0
        var errors=0
        var checked=0
        for(item in tracked) {
            if(!visible || Thread.currentThread().isInterrupted) break
            checked++
            runCatching {
                val observation=ApplicationInventory.inspect(applicationContext,item.packageName)
                ApplicationCenterRepository(applicationContext).use { repository ->
                    if(repository.audit(item.packageName,observation).event!=null) changed++
                }
            }.onFailure { errors++ }
        }
        "Проверено по запросу: $checked из ${tracked.size} записей. "+
            "Событий: $changed · ошибок Android/SQLite: $errors. "+
            "Недоступность пакета НЕ означает удаление."
    }

    private fun inspect() {
        val pkg=selectedPackage() ?: return
        work { ApplicationInventory.inspect(applicationContext,pkg).report() }
    }

    private fun showEvents() = work {
        ApplicationCenterRepository(applicationContext).use { repository ->
            val events=repository.events(limit=80)
            if(events.isEmpty()) "История изменений пока пуста."
            else events.joinToString("\n\n") { e ->
                DateFormat.getDateTimeInstance().format(Date(e.recordedAtMs))+
                    " · "+e.packageName+"\n"+e.kind+" · "+e.details
            }
        }
    }

    private fun confirmRemove() {
        val pkg=selectedPackage() ?: return
        AlertDialog.Builder(this).setTitle("Удалить из библиотеки?")
            .setMessage("Удалятся только сохранённая заметка, категория, избранное "+
                "и события наблюдения для $pkg. Само приложение и игровые сессии "+
                "останутся нетронутыми.")
            .setPositiveButton("Удалить") { _,_ ->
                work {
                    val removed=ApplicationCenterRepository(applicationContext).use {
                        it.remove(pkg)
                    }
                    if(removed) "Удалено из локальной библиотеки: $pkg."
                    else "Пакет не найден в библиотеке."
                }
            }.setNegativeButton("Отмена",null).show()
    }

    private fun usage(days:Int) {
        val pkg=selectedPackage() ?: return
        work { ApplicationUsageInspector.report(applicationContext,pkg,days) }
    }

    private fun openSystem(intent:Intent) {
        runCatching { startActivity(intent) }.onFailure {
            show("Системный экран недоступен на этой прошивке: "+
                it.javaClass.simpleName)
        }
    }

    private fun launchApp() {
        val pkg=selectedPackage() ?: return
        val intent=ApplicationInventory.launchIntent(this,pkg)
        if(intent==null) show("Android не вернул действие запуска. "+
            "Попробуйте системные настройки приложения.")
        else openSystem(intent)
    }

    private fun exportCsv() {
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"legion-application-center-alpha21.csv")
            },EXPORT_CSV)
        }.onFailure { show("Не удалось открыть системный выбор файла: "+
            it.javaClass.simpleName) }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=EXPORT_CSV || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        work {
            ApplicationCenterRepository(applicationContext).use { repository ->
                (contentResolver.openOutputStream(uri,"w")
                    ?: error("Не удалось открыть документ для записи")).use { stream ->
                    OutputStreamWriter(stream,Charsets.UTF_8).use { writer ->
                        repository.writeCsv(writer)
                    }
                }
            }
            "Библиотека и события сохранены в CSV по выбранному пути. "+
                "Содержимое заметок стоит проверить перед публикацией."
        }
    }

    override fun onStart() {
        super.onStart()
        visible=true
    }

    override fun onStop() {
        visible=false // Manual library scan stops when user leaves this screen.
        super.onStop()
    }

    override fun onDestroy() {
        visible=false
        worker.shutdown()
        super.onDestroy()
    }
}
