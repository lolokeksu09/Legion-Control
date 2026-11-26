package dev.lolokeksu.legioncontrol

import android.Manifest
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
class GameSessionFragment : Fragment() {
    private lateinit var viewModel: GameSessionViewModel
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp,30.dp,20.dp,32.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun description(t: String) = TextView(requireContext()).apply {
            text=t
            textSize=13f
            setTextColor(Color.LTGRAY)
            setPadding(0,9.dp,0,12.dp)
        }
        fun action(t: String, featureId: String? = null, click: () -> Unit) {
            root.addView(Button(requireContext()).apply {
                if(featureId!=null) FeatureReveal.bind(this,featureId)
                text=t
                minHeight=48.dp
                setOnClickListener { click() }
            })
        }
        root.addView(TextView(requireContext()).apply {
            text="LEGION · GAME SESSION"
            textSize=23f
            setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
        })
        root.addView(description(
            "Выберите игру и начните запись состояния устройства. Root и Shizuku не требуются."
        ))
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ПОДГОТОВКА"))
        action("Выбрать установленное приложение") { chooseApp() }
        packageInput = EditText(requireContext()).apply {
            hint="Имя пакета · com.example.game"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType=android.text.InputType.TYPE_CLASS_TEXT
        }
        root.addView(packageInput)
        labelInput = EditText(requireContext()).apply {
            hint="Название (необязательно)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(labelInput)

        // Keep the primary action within the first viewport of the module.
        // Optional settings and extended documentation follow the recording controls.
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "УПРАВЛЕНИЕ ЗАПИСЬЮ"))
        action("НАЧАТЬ ЗАПИСЬ") { startRecording() }
        reportView = TextView(requireContext()).apply {
            text="Выберите приложение и начните запись. Для TCP-теста или Smart Profiles " +
                "сначала задайте дополнительные настройки ниже."
            textSize=14f
            setTextColor(Color.WHITE)
            setLineSpacing(4f,1f)
            setPadding(0,14.dp,0,20.dp)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        LegionUiSupport.reportSurface(reportView)
        root.addView(reportView)
        action("Открыть выбранное приложение") { openSelectedApp() }
        action("ОСТАНОВИТЬ ЗАПИСЬ") { stopRecording() }
        action("Добавить метку во время записи", "session.mark") { addUserMark() }

        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ДОПОЛНИТЕЛЬНЫЕ НАСТРОЙКИ"))
        action("Smart Profiles · сохранённые настройки") {
            ModuleNavigation.open(this,SmartProfilesActivity::class.java)
        }
        tcpOption = CheckBox(requireContext()).apply {
            text="Дополнительно: TCP-тест one.one.one.one:443 примерно раз в 2 минуты"
            isChecked=false
            setTextColor(Color.WHITE)
            setPadding(0,12.dp,0,12.dp)
        }
        root.addView(tcpOption)
        root.addView(description(
            "Без этого флажка тестовые TCP-соединения не выполняются. " +
            "Данные батареи и сети хранятся локально. " +
            "Запись не измеряет FPS игры и не перехватывает трафик."
        ))
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "АНАЛИТИКА И ИСТОРИЯ"))
        action("История и отчёты игры") {
            val pkg = packageInput.text.toString().trim().takeIf(packagePattern::matches)
            startActivity(SessionLibraryActivity.intent(requireContext(), pkg))
        }
        action("Разрешить уведомления о записи (Android 13+)") {
            if (requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),REQUEST_NOTIFICATION)
            else reportView.text="Уведомления уже разрешены."
        }
        action("Обновить и показать последние сессии") { refresh() }
        action("Сравнить две последние сессии") { compare() }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ЭКСПОРТ"))
        action("Сохранить историю замеров CSV", "session.csv") { createFile(REQUEST_CSV) }
        action("Сохранить полный архив сессий ZIP") { createFile(REQUEST_ZIP) }
        action("Статистика использования приложений (опционально)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        root.addView(description(
            "Для публикации на 4PDA: Android 13+, запись начинается только по нажатию, " +
            "не стартует после перезагрузки и не передаёт историю на сервер. " +
            "Android может остановить службу из-за ограничений прошивки."
        ))
        timeline = GameSessionTimelineView(requireContext())
        root.addView(timeline, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 240.dp))
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.SESSION)
        viewModel = ViewModelProvider(this)[GameSessionViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { snapshot ->
            reportView.text = snapshot.report
            snapshot.timeline?.let { timeline.setPoints(it) }
        }
        if (savedInstanceState != null) {
            selectedPackage = savedInstanceState.getString("selectedPackage").orEmpty()
            packageInput.setText(savedInstanceState.getString("packageInput").orEmpty())
            labelInput.setText(savedInstanceState.getString("labelInput").orEmpty())
            tcpOption.isChecked = savedInstanceState.getBoolean("tcpOptIn", false)
        } else arguments?.getString("smart_package")?.takeIf {
            packagePattern.matches(it)
        }?.let {
            packageInput.setText(it)
            labelInput.setText(arguments?.getString("smart_label") ?: it)
            tcpOption.isChecked=(arguments?.getBoolean("smart_tcp",false) ?: false)
            reportView.text="Профиль загружен. Нажмите «НАЧАТЬ ЗАПИСЬ», затем откройте игру."
        }
    }

    private fun chooseApp() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = requireContext().packageManager.queryIntentActivities(
            intent,PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo?.packageName != requireContext().packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.loadLabel(requireContext().packageManager).toString()
            })
        if(apps.isEmpty()) {
            reportView.text="Android не вернул список приложений. Введите пакет вручную."
            return
        }
        val labels = apps.map {
            it.loadLabel(requireContext().packageManager).toString()+"\n"+it.activityInfo.packageName
        }.toTypedArray()
        LegionDialogs.builder(requireContext()).setTitle("Выбрать приложение")
            .setItems(labels) { _, which ->
                val app = apps[which]
                selectedPackage = app.activityInfo.packageName
                packageInput.setText(selectedPackage)
                labelInput.setText(app.loadLabel(requireContext().packageManager).toString())
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
            GameSessionRecorderService.start(requireContext(),pkg,label,tcpOption.isChecked)
            reportView.text="Запрошен запуск записи для $label. Проверьте уведомление; " +
                "потом откройте приложение. При отказе Android запись может не начаться."
        } catch (e: Exception) {
            reportView.text="Android отказал в запуске: "+e.javaClass.simpleName
        }
    }

    private fun stopRecording() {
        try {
            GameSessionRecorderService.stop(requireContext())
            reportView.text="Отправлена команда остановки. Обновите историю через несколько секунд."
        } catch(e: Exception) {
            reportView.text="Не удалось остановить службу: "+e.javaClass.simpleName
        }
    }

    /** Manual annotations are queued only by an active recorder; no implicit START. */
    private fun addUserMark() {
        val field=EditText(requireContext()).apply {
            hint="Например: лаг при смене Wi-Fi"
            setSingleLine(true)
            filters=arrayOf(android.text.InputFilter.LengthFilter(120))
            setTextColor(Color.BLACK)
        }
        LegionDialogs.builder(requireContext())
            .setTitle("Отметить момент во время записи")
            .setView(field)
            .setPositiveButton("Добавить") { _,_ ->
                val note=SessionSignals.normalizeMarker(field.text.toString())
                if(note==null) reportView.text="Метка пуста."
                else runCatching {
                    GameSessionRecorderService.mark(requireContext(),note)
                    reportView.text="Отправлена ручная метка. Она сохранится только " +
                        "если Game Session сейчас записывает. Проверить можно через Session Signals."
                }.onFailure { reportView.text="Метка не отправлена: "+it.javaClass.simpleName }
            }.setNegativeButton("Отмена",null).show()
    }

    private fun openSelectedApp() {
        val (pkg,_)=target() ?: return
        val launch=requireContext().packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) reportView.text="Android не разрешил открыть этот пакет; откройте приложение вручную."
        else runCatching { startActivity(launch) }.onFailure {
            reportView.text="Не удалось открыть приложение: "+it.javaClass.simpleName
        }
    }

    private fun refresh() = viewModel.refresh()

    private fun compare() = viewModel.compare()

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
        if (resultCode != Activity.RESULT_OK || requestCode !in listOf(REQUEST_CSV,REQUEST_ZIP)) return
        val uri = data?.data ?: return
        // The system picker stays in Activity; content writes are in the ViewModel.
        viewModel.export(uri, requestCode == REQUEST_ZIP)
    }

    private val Int.dp: Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("packageInput", packageInput.text.toString())
        outState.putString("labelInput", labelInput.text.toString())
        outState.putString("selectedPackage", selectedPackage)
        outState.putBoolean("tcpOptIn", tcpOption.isChecked)
        super.onSaveInstanceState(outState)
    }
}
