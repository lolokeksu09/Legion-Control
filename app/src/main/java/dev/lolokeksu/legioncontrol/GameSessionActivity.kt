package dev.lolokeksu.legioncontrol

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Universal app selector. The selected package is a session label; no privileged
 * claim is made that Android allows checking another app's real-time FPS/foreground.
 */
class GameSessionActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var packageInput: EditText
    private lateinit var labelInput: EditText
    private lateinit var tcpOption: CheckBox
    private lateinit var reportView: TextView
    private lateinit var timeline: GameSessionTimelineView
    private var selectedPackage = ""
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    companion object {
        private const val REQUEST_CSV = 1702
        private const val REQUEST_ZIP = 1703
        private const val REQUEST_NOTIFICATION = 1704
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp,30.dp,20.dp,32.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun description(t: String) = TextView(this).apply {
            text=t
            textSize=13f
            setTextColor(Color.LTGRAY)
            setPadding(0,9.dp,0,12.dp)
        }
        fun action(t: String, click: () -> Unit) {
            root.addView(Button(this).apply {
                text=t
                minHeight=48.dp
                setOnClickListener { click() }
            })
        }
        root.addView(TextView(this).apply {
            text="LEGION · GAME SESSION"
            textSize=23f
            setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
        })
        root.addView(description(
            "Для любой установленной игры или приложения. Ручная запись состояния телефона " +
            "продолжается при переходе в выбранное приложение. Ни Root, ни Shizuku не требуются."
        ))
        action("Выбрать установленное приложение") { chooseApp() }
        packageInput = EditText(this).apply {
            hint="Имя пакета · com.example.game"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType=android.text.InputType.TYPE_CLASS_TEXT
        }
        root.addView(packageInput)
        labelInput = EditText(this).apply {
            hint="Название (необязательно)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(labelInput)
        tcpOption = CheckBox(this).apply {
            text="Дополнительно: TCP-тест one.one.one.one:443 примерно раз в 2 минуты"
            isChecked=false
            setTextColor(Color.WHITE)
            setPadding(0,12.dp,0,12.dp)
        }
        root.addView(tcpOption)
        root.addView(description(
            "Без этого флажка никакие тестовые TCP-соединения не выполняются. " +
            "Обычные изменения сети и показания батареи записываются только локально. " +
            "Запись не включает настоящий FPS, ping игрового сервера или перехват трафика."
        ))
        action("НАЧАТЬ ЗАПИСЬ") { startRecording() }
        action("Открыть выбранное приложение") { openSelectedApp() }
        action("ОСТАНОВИТЬ ЗАПИСЬ") { stopRecording() }
        action("Разрешить уведомления о записи (Android 13+)") {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),REQUEST_NOTIFICATION)
            else reportView.text="Уведомления уже разрешены."
        }
        action("Обновить и показать последние сессии") { refresh() }
        action("Сравнить две последние сессии") { compare() }
        action("Сохранить историю замеров CSV") { createFile(REQUEST_CSV) }
        action("Сохранить полный архив сессий ZIP") { createFile(REQUEST_ZIP) }
        action("Статистика использования приложений (опционально)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        root.addView(description(
            "Для публикации на 4PDA: Android 13+, запись начинается только по нажатию, " +
            "не стартует после перезагрузки и не передаёт историю на сервер. " +
            "Android может остановить службу из-за ограничений прошивки."
        ))
        reportView = TextView(this).apply {
            text="Выберите приложение, нажмите «НАЧАТЬ ЗАПИСЬ», затем откройте игру. " +
                "При завершении нажмите «ОСТАНОВИТЬ ЗАПИСЬ» в приложении или уведомлении."
            textSize=14f
            setTextColor(Color.WHITE)
            setLineSpacing(4f,1f)
            setPadding(0,14.dp,0,20.dp)
        }
        root.addView(reportView)
        timeline = GameSessionTimelineView(this)
        root.addView(timeline, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 240.dp))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun chooseApp() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(
            intent,PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo?.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.loadLabel(packageManager).toString()
            })
        if(apps.isEmpty()) {
            reportView.text="Android не вернул список приложений. Введите пакет вручную."
            return
        }
        val labels = apps.map {
            it.loadLabel(packageManager).toString()+"\n"+it.activityInfo.packageName
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Выбрать приложение")
            .setItems(labels) { _, which ->
                val app = apps[which]
                selectedPackage = app.activityInfo.packageName
                packageInput.setText(selectedPackage)
                labelInput.setText(app.loadLabel(packageManager).toString())
                reportView.text="Выбрано: "+labelInput.text+" ("+selectedPackage+"). Запись ещё не запущена."
            }
            .setNegativeButton("Отмена",null).show()
    }

    private fun target(): Pair<String,String>? {
        val pkg=packageInput.text.toString().trim()
        if (!packagePattern.matches(pkg)) {
            reportView.text="Некорректное имя пакета. Выберите приложение из списка."
            return null
        }
        val label=labelInput.text.toString().trim().take(100).ifBlank { pkg }
        selectedPackage=pkg
        return pkg to label
    }

    private fun startRecording() {
        val (pkg,label)=target() ?: return
        try {
            GameSessionRecorderService.start(this,pkg,label,tcpOption.isChecked)
            reportView.text="Запрошен запуск записи для $label. Проверьте уведомление; " +
                "потом откройте приложение. При отказе Android запись может не начаться."
        } catch (e: Exception) {
            reportView.text="Android отказал в запуске: "+e.javaClass.simpleName
        }
    }

    private fun stopRecording() {
        try {
            GameSessionRecorderService.stop(this)
            reportView.text="Отправлена команда остановки. Обновите историю через несколько секунд."
        } catch(e: Exception) {
            reportView.text="Не удалось остановить службу: "+e.javaClass.simpleName
        }
    }

    private fun openSelectedApp() {
        val (pkg,_)=target() ?: return
        val launch=packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) reportView.text="Android не разрешил открыть этот пакет; откройте приложение вручную."
        else runCatching { startActivity(launch) }.onFailure {
            reportView.text="Не удалось открыть приложение: "+it.javaClass.simpleName
        }
    }

    private fun refresh() = runWorker {
        GameSessionRepository(applicationContext).use { repo ->
            val rows=repo.recent(8)
            if(rows.isEmpty()) "Записанных сессий ещё нет." else buildString {
                val graphPoints=repo.points(rows[0].id)
                runOnUiThread { if (!isDestroyed) timeline.setPoints(graphPoints) }
                append("ПОСЛЕДНИЕ СЕССИИ · ").append(rows.size).append('\n')
                for(row in rows) {
                    val points=repo.points(row.id)
                    val s=GameSessionAnalysis.summarize(row,points)
                    append("\n").append(date(row.startedAtMs)).append(" · ")
                        .append(GameSessionAnalysis.render(s)).append('\n')
                    if(points.size >= 10000) append("Показаны первые 10 000 точек; полный CSV доступен в архиве.\n")
                    for(event in repo.events(row.id,5))
                        append("  ").append(date(event.timeMs)).append(" · ")
                            .append(event.kind).append(": ").append(event.details).append('\n')
                }
            }
        }
    }

    private fun compare() = runWorker {
        GameSessionRepository(applicationContext).use { repo ->
            val latest=repo.recent(2)
            if(latest.size < 2) "Для сравнения нужны две сессии."
            else GameSessionAnalysis.compare(
                GameSessionAnalysis.summarize(latest[0],repo.points(latest[0].id)),
                GameSessionAnalysis.summarize(latest[1],repo.points(latest[1].id))
            )
        }
    }

    private fun createFile(requestCode: Int) {
        val isZip=requestCode==REQUEST_ZIP
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type=if(isZip)"application/zip" else "text/csv"
            putExtra(Intent.EXTRA_TITLE,if(isZip)"legion-game-sessions.zip" else "legion-game-samples.csv")
        }
        runCatching { startActivityForResult(intent,requestCode) }.onFailure {
            reportView.text="Не удалось открыть выбор файла: "+it.javaClass.simpleName
        }
    }

    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode != RESULT_OK || requestCode !in listOf(REQUEST_CSV,REQUEST_ZIP)) return
        val uri=data?.data ?: return
        runWorker {
            GameSessionRepository(applicationContext).use { repo ->
                val output=contentResolver.openOutputStream(uri,"w") ?: error("Нет потока записи")
                output.use {
                    if(requestCode==REQUEST_ZIP) repo.exportZip(it)
                    else BufferedWriter(OutputStreamWriter(it,Charsets.UTF_8)).use { writer ->
                        repo.writeCsv(writer,"samples")
                    }
                }
            }
            "Экспорт завершён через выбранный системный документ."
        }
    }

    private fun runWorker(job: () -> String) {
        reportView.text="Обработка локальных данных…"
        worker.execute {
            val text=runCatching { job() }.getOrElse { "Ошибка: "+it.javaClass.simpleName }
            runOnUiThread { if(!isFinishing && !isDestroyed) reportView.text=text }
        }
    }

    private fun date(ms: Long): String =
        SimpleDateFormat("dd.MM HH:mm:ss",Locale.getDefault()).format(Date(ms))

    private val Int.dp: Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
