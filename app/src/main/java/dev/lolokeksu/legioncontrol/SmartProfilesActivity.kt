package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.util.concurrent.Executors

/**
 * One screen for profile CRUD, rule configuration, on-demand system actions,
 * history and JSON restore. Database and network operations never use UI thread.
 */
class SmartProfilesActivity : Activity() {
    companion object {
        private const val EXPORT = 1801
        private const val IMPORT = 1802
    }
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var packageText:EditText
    private lateinit var labelText:EditText
    private lateinit var hotText:EditText
    private lateinit var lowText:EditText
    private lateinit var tcpBox:CheckBox
    private lateinit var result:TextView

    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,28.dp,18.dp,40.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun note(t:String) { root.addView(TextView(this).apply {
            text=t;textSize=13f;setTextColor(0xFFAFAFAF.toInt());setPadding(0,9.dp,0,12.dp)
        }) }
        fun action(t:String,run:()->Unit) {
            root.addView(Button(this).apply {
                text=t;minHeight=48.dp;setOnClickListener { run() }
            })
        }
        fun edit(hintText:String,numeric:Boolean=false):EditText=EditText(this).apply {
            hint=hintText
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER
            root.addView(this)
        }
        root.addView(TextView(this).apply {
            text="LEGION CONTROL · SMART PROFILES"
            setTextColor(Color.WHITE);textSize=20f;setTypeface(null,Typeface.BOLD)
        })
        note("Для любых игр и приложений Android 13+. Настройки не требуют Root/Shizuku. " +
            "Запись запускается вручную; системные изменения никогда не выполняются автоматически.")
        action("Выбрать установленное приложение") { chooseInstalled() }
        action("Загрузить сохранённый профиль") { loadPicker() }
        packageText=edit("Имя пакета приложения")
        labelText=edit("Название профиля")
        hotText=edit("Температура батареи для предупреждения · °C × 10",true).also { it.setText("420") }
        lowText=edit("Предупреждение о заряде · %",true).also { it.setText("20") }
        tcpBox=CheckBox(this).apply {
            text="Разрешить TCP-тест при записи (внешняя сеть)"
            setTextColor(Color.WHITE)
            isChecked=false
        }
        root.addView(tcpBox)
        action("СОХРАНИТЬ ПРОФИЛЬ") { save() }
        action("Настроить 6 правил профиля") { configureRules() }
        action("Открыть Game Session с этим профилем") { openSession() }
        action("Удалить профиль (с подтверждением)") { confirmDelete() }
        note("Правила оцениваются каждые ~15 секунд только во время явно запущенной записи. " +
            "Если изменить правила в ходе записи, новые настройки применятся в следующей сессии.")
        action("EXPERIMENT LAB · A/B сравнения для выбранного приложения") {
            val pkg=packageNameInput()
            startActivity(Intent(this,ExperimentLabActivity::class.java).apply {
                if(SmartProfilesRepository.validatePackage(pkg)) putExtra("experiment_package",pkg)
            })
        }
        action("Сводка по выбранному приложению · 24 часа") { insights(1) }
        action("Сводка · 7 дней") { insights(7) }
        action("Сводка · 30 дней") { insights(30) }
        action("Последние предупреждения") { events() }
        action("NETWORK QUALITY LAB · разовый TCP/DNS-тест") { networkProbe() }
        note("Сетевой тест запускается строго по нажатию. Не измеряет ping игрового сервера.")
        action("Проверить возможности устройства") {
            show(SmartCompatibilityBackend.inspect(this))
        }
        action("Состояние системных переключателей") {
            show(SmartSystemControls.status(this))
        }
        action("Громкость мультимедиа · 25%") { show(SmartSystemControls.setMediaVolume(this,25)) }
        action("Громкость мультимедиа · 75%") { show(SmartSystemControls.setMediaVolume(this,75)) }
        action("Громкость · своё значение") { setCustomVolume() }
        action("Разрешить доступ к «Не беспокоить»") {
            launchSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        action("Включить «Не беспокоить»") {
            show(SmartSystemControls.setDoNotDisturb(this,true))
        }
        action("Выключить «Не беспокоить»") {
            show(SmartSystemControls.setDoNotDisturb(this,false))
        }
        action("Разрешить изменение системных настроек") {
            launchSettings(SmartSystemControls.writeSettingsIntent(this))
        }
        action("Яркость 50% (с сохранением исходного значения)") {
            show(SmartSystemControls.setBrightness(this,50))
        }
        action("Тайм-аут экрана · 2 минуты") {
            show(SmartSystemControls.setScreenTimeout(this,120))
        }
        action("Восстановить ранее сохранённые экранные параметры") {
            show(SmartSystemControls.restoreScreenSettings(this))
        }
        note("Яркость/тайм-аут требуют отдельного спецдоступа WRITE_SETTINGS. " +
            "На некоторых прошивках изменение DND или системных настроек будет заблокировано.")
        action("Экспортировать профили и правила JSON") { createBackup() }
        action("Импортировать профили JSON (с подтверждением)") { selectBackup() }
        note("Backup включает профили и правила, не историю сессий. " +
            "Экспорт истории замеров CSV/ZIP остаётся в Game Session.")
        note("Плитку Legion Control можно добавить вручную через редактор шторки быстрых настроек. " +
            "Она открывает этот экран, не запускает запись скрыто.")
        result=TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize=14f
            setPadding(0,20.dp,0,16.dp)
            setLineSpacing(4f,1f)
            text="Профили хранятся только на устройстве. Выберите приложение или загрузите профиль."
        }
        root.addView(result)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun show(message:String) { result.text=message }

    private fun runWorker(block:()->String) {
        show("Операция с локальными данными…")
        worker.execute {
            val message=runCatching { block() }
                .getOrElse { "Ошибка: ${it.javaClass.simpleName}: ${it.message.orEmpty().take(160)}" }
            runOnUiThread { if(!isFinishing && !isDestroyed) show(message) }
        }
    }

    private fun packageNameInput():String=packageText.text.toString().trim()

    private fun save() {
        val pkg=packageNameInput()
        val label=labelText.text.toString().trim()
        val hot=hotText.text.toString().toIntOrNull()
        val low=lowText.text.toString().toIntOrNull()
        if(!SmartProfilesRepository.validatePackage(pkg) || label.isBlank() || label.length>100 ||
            hot==null || hot !in 300..550 || low==null || low !in 5..80) {
            show("Некорректные данные: пакет Android, название (до 100), температура 300–550, заряд 5–80.")
            return
        }
        val tcp=tcpBox.isChecked
        runWorker {
            SmartProfilesRepository(applicationContext).use {
                it.save(pkg,label,hot,low,tcp)
            }
            "Сохранено: $label ($pkg). Все 6 правил доступны в настройке."
        }
    }

    private fun chooseInstalled() {
        val search=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val applications=packageManager.queryIntentActivities(search,
            PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo?.packageName!=packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if(applications.isEmpty()) { show("Лаунчер не вернул приложения; введите пакет вручную.");return }
        AlertDialog.Builder(this).setTitle("Выбор приложения")
            .setItems(applications.map {
                it.loadLabel(packageManager).toString()+"\n"+it.activityInfo.packageName
            }.toTypedArray()) { _,index ->
                val app=applications[index]
                packageText.setText(app.activityInfo.packageName)
                labelText.setText(app.loadLabel(packageManager).toString())
                show("Выбрано приложение; сохраните профиль.")
            }.setNegativeButton("Отмена",null).show()
    }

    private fun loadPicker() = runWorker {
        SmartProfilesRepository(applicationContext).use { repo ->
            val profiles=repo.list()
            runOnUiThread {
                if(!isFinishing && !isDestroyed) {
                    if(profiles.isEmpty()) show("Пока нет сохранённых профилей.")
                    else AlertDialog.Builder(this).setTitle("Сохранённые профили")
                        .setItems(profiles.map { it.label+"\n"+it.packageName }.toTypedArray()) { _,index ->
                            val p=profiles[index]
                            packageText.setText(p.packageName)
                            labelText.setText(p.label)
                            hotText.setText(p.hotDeciC.toString())
                            lowText.setText(p.lowPercent.toString())
                            tcpBox.isChecked=p.tcpOptIn
                            show("Загружен профиль: "+p.label)
                        }.setNegativeButton("Отмена",null).show()
                }
            }
            "Найдено профилей: ${profiles.size}"
        }
    }

    private fun configureRules() {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) {
            show("Выберите корректный пакет приложения.");return
        }
        runWorker {
            SmartProfilesRepository(applicationContext).use { repo ->
                val rules=repo.rules(pkg)
                if(rules.isEmpty()) "Сначала сохраните профиль." else {
                    val flags=rules.map { it.enabled }.toBooleanArray()
                    runOnUiThread {
                        if(!isFinishing && !isDestroyed)
                            AlertDialog.Builder(this).setTitle("Правила · "+pkg)
                                .setMultiChoiceItems(rules.map {
                                    it.kind.title+" · "+it.kind.describe(it.threshold)
                                }.toTypedArray(),flags) { _,index,checked ->
                                    flags[index]=checked
                                }
                                .setPositiveButton("Сохранить") { _,_ ->
                                    runWorker {
                                        SmartProfilesRepository(applicationContext).use { p ->
                                            rules.forEachIndexed { i,r ->
                                                p.setRule(pkg,r.kind,flags[i],r.cooldownMs)
                                            }
                                        }
                                        "Правила сохранены. В текущей записи изменения не применяются до нового запуска."
                                    }
                                }.setNegativeButton("Отмена",null).show()
                    }
                    "Настройка правил $pkg"
                }
            }
        }
    }

    private fun openSession() {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) { show("Сначала выберите пакет.");return }
        startActivity(Intent(this,GameSessionActivity::class.java).apply {
            putExtra("smart_package",pkg)
            putExtra("smart_label",labelText.text.toString().trim().ifBlank { pkg })
            putExtra("smart_tcp",tcpBox.isChecked)
        })
    }

    private fun confirmDelete() {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) { show("Не выбран профиль.");return }
        AlertDialog.Builder(this).setTitle("Удалить профиль?")
            .setMessage("Профиль $pkg и его правила будут удалены. Старые сессии сохранятся.")
            .setPositiveButton("Удалить") { _,_ ->
                runWorker {
                    val deleted=SmartProfilesRepository(applicationContext).use { it.delete(pkg) }
                    if(deleted) "Профиль удалён. История сессий сохранена."
                    else "Такого профиля нет."
                }
            }.setNegativeButton("Отмена",null).show()
    }

    private fun insights(days:Int) {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) { show("Сначала выберите пакет.");return }
        runWorker { SmartSessionInsights.summarize(applicationContext,pkg,days).textReport() }
    }

    private fun events() = runWorker {
        SmartProfilesRepository(applicationContext).use { repo ->
            repo.events().joinToString("\n") {
                "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.timeMs))} · "+it.packageName+
                " · "+it.kind+" · "+it.details
            }.ifBlank { "Предупреждений пока нет." }
        }
    }

    private fun networkProbe() = runWorker {
        SmartNetworkLab.inspect(applicationContext)
    }

    private fun setCustomVolume() {
        val edit=EditText(this).apply {
            hint="0–100"
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this).setTitle("Громкость мультимедиа, %")
            .setView(edit).setPositiveButton("Применить") { _,_ ->
                val p=edit.text.toString().toIntOrNull()
                if(p==null || p !in 0..100) show("Укажите процент 0–100.")
                else show(SmartSystemControls.setMediaVolume(this,p))
            }.setNegativeButton("Отмена",null).show()
    }

    private fun launchSettings(intent:Intent) {
        runCatching { startActivity(intent) }.onFailure {
            show("Не удалось открыть настройки: ${it.javaClass.simpleName}")
        }
    }

    private fun createBackup() {
        launchPicker(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="application/json"
            putExtra(Intent.EXTRA_TITLE,"legion-smart-profiles.json")
        },EXPORT)
    }
    private fun selectBackup() {
        launchPicker(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="application/json"
        },IMPORT)
    }
    private fun launchPicker(intent:Intent,code:Int) {
        runCatching { startActivityForResult(intent,code) }.onFailure {
            show("Документ не открылся: ${it.javaClass.simpleName}")
        }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        when(requestCode) {
            EXPORT -> runWorker {
                SmartProfilesRepository(applicationContext).use { repo ->
                    (contentResolver.openOutputStream(uri,"w") ?: error("No output stream"))
                        .use { repo.writeBackup(it) }
                }
                "JSON профилей экспортирован через системный выбор файла."
            }
            IMPORT -> AlertDialog.Builder(this).setTitle("Импортировать профили?")
                .setMessage("Совпадающие профили и их настройки будут перезаписаны. " +
                    "Другие профили и история измерений сохранятся. " +
                    "Файл должен быть экспортом Legion Control версии 1.")
                .setPositiveButton("Импортировать") { _,_ ->
                    runWorker {
                        val count=SmartProfilesRepository(applicationContext).use { repo ->
                            (contentResolver.openInputStream(uri) ?: error("No input stream"))
                                .use { repo.importBackup(it) }
                        }
                        "Импортировано профилей: $count. История не изменена."
                    }
                }.setNegativeButton("Отмена",null).show()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
