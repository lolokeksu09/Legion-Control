package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.ViewModelProvider
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
class SmartProfilesFragment : Fragment() {
    companion object {
        private const val EXPORT = 1801
        private const val IMPORT = 1802
    }
    private lateinit var viewModel: SmartProfilesViewModel
    private lateinit var packageText:EditText
    private lateinit var labelText:EditText
    private lateinit var hotText:EditText
    private lateinit var lowText:EditText
    private lateinit var tcpBox:CheckBox
    private lateinit var result:TextView

    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,28.dp,18.dp,40.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun note(t:String) { root.addView(TextView(requireContext()).apply {
            text=t;textSize=13f;setTextColor(0xFFAFAFAF.toInt());setPadding(0,9.dp,0,12.dp)
        }) }
        fun action(t:String,run:()->Unit) {
            root.addView(Button(requireContext()).apply {
                text=t;minHeight=48.dp;setOnClickListener { run() }
            })
        }
        fun edit(hintText:String,numeric:Boolean=false):EditText=EditText(requireContext()).apply {
            hint=hintText
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER
            root.addView(this)
        }
        root.addView(TextView(requireContext()).apply {
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
        tcpBox=CheckBox(requireContext()).apply {
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
        action("APPLICATION CENTER · библиотека, версии и права") {
            val pkg=packageNameInput()
            startActivity(Intent(requireContext(),ApplicationCenterActivity::class.java).apply {
                if(SmartProfilesRepository.validatePackage(pkg)) putExtra("app_center_package",pkg)
            })
        }
        action("EXPERIMENT LAB · A/B сравнения для выбранного приложения") {
            val pkg=packageNameInput()
            startActivity(Intent(requireContext(),ExperimentLabActivity::class.java).apply {
                if(SmartProfilesRepository.validatePackage(pkg)) putExtra("experiment_package",pkg)
            })
        }
        action("Сводка по выбранному приложению · 24 часа") { insights(1) }
        action("Сводка · 7 дней") { insights(7) }
        action("Сводка · 30 дней") { insights(30) }
        action("Последние предупреждения") { events() }
        action("CONNECTIVITY OBSERVATORY · подробный DNS/TCP/TLS анализ") {
            startActivity(Intent(requireContext(),ConnectivityLabActivity::class.java))
        }
        action("NETWORK QUALITY LAB · разовый TCP/DNS-тест") { networkProbe() }
        note("Сетевой тест запускается строго по нажатию. Не измеряет ping игрового сервера.")
        action("Проверить возможности устройства") {
            show(SmartCompatibilityBackend.inspect(requireContext()))
        }
        action("Состояние системных переключателей") {
            show(SmartSystemControls.status(requireContext()))
        }
        action("Громкость мультимедиа · 25%") { show(SmartSystemControls.setMediaVolume(requireContext(),25)) }
        action("Громкость мультимедиа · 75%") { show(SmartSystemControls.setMediaVolume(requireContext(),75)) }
        action("Громкость · своё значение") { setCustomVolume() }
        action("Разрешить доступ к «Не беспокоить»") {
            launchSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        action("Включить «Не беспокоить»") {
            show(SmartSystemControls.setDoNotDisturb(requireContext(),true))
        }
        action("Выключить «Не беспокоить»") {
            show(SmartSystemControls.setDoNotDisturb(requireContext(),false))
        }
        action("Разрешить изменение системных настроек") {
            launchSettings(SmartSystemControls.writeSettingsIntent(requireContext()))
        }
        action("Яркость 50% (с сохранением исходного значения)") {
            show(SmartSystemControls.setBrightness(requireContext(),50))
        }
        action("Тайм-аут экрана · 2 минуты") {
            show(SmartSystemControls.setScreenTimeout(requireContext(),120))
        }
        action("Восстановить ранее сохранённые экранные параметры") {
            show(SmartSystemControls.restoreScreenSettings(requireContext()))
        }
        note("Яркость/тайм-аут требуют отдельного спецдоступа WRITE_SETTINGS. " +
            "На некоторых прошивках изменение DND или системных настроек будет заблокировано.")
        action("Экспортировать профили и правила JSON") { createBackup() }
        action("Импортировать профили JSON (с подтверждением)") { selectBackup() }
        note("Backup включает профили и правила, не историю сессий. " +
            "Экспорт истории замеров CSV/ZIP остаётся в Game Session.")
        note("Плитку Legion Control можно добавить вручную через редактор шторки быстрых настроек. " +
            "Она открывает этот экран, не запускает запись скрыто.")
        result=TextView(requireContext()).apply {
            setTextColor(Color.WHITE)
            textSize=14f
            setPadding(0,20.dp,0,16.dp)
            setLineSpacing(4f,1f)
            text="Профили хранятся только на устройстве. Выберите приложение или загрузите профиль."
        }
        root.addView(result)
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        viewModel=ViewModelProvider(this)[SmartProfilesViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { update ->
            show(update.message)
            update.profiles?.let { profiles ->
                viewModel.consumeDialog(update.nonce)
                showSavedProfiles(profiles)
            }
            update.rules?.let { rules ->
                viewModel.consumeDialog(update.nonce)
                showRulesDialog(update.rulesPackage.orEmpty(), rules)
            }
        }
        if (savedInstanceState != null) {
            packageText.setText(savedInstanceState.getString("pkg").orEmpty())
            labelText.setText(savedInstanceState.getString("label").orEmpty())
            hotText.setText(savedInstanceState.getString("hot", "420"))
            lowText.setText(savedInstanceState.getString("low", "20"))
            tcpBox.isChecked = savedInstanceState.getBoolean("tcp", false)
        } else arguments?.getString("app_center_package")?.takeIf {
            SmartProfilesRepository.validatePackage(it)
        }?.let { packageText.setText(it) }
    }

    private fun show(message:String) { result.text=message }

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
        viewModel.save(pkg, label, hot, low, tcpBox.isChecked)
    }

    private fun chooseInstalled() {
        val search=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val applications=requireContext().packageManager.queryIntentActivities(search,
            PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo?.packageName!=requireContext().packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(requireContext().packageManager).toString().lowercase() }
        if(applications.isEmpty()) { show("Лаунчер не вернул приложения; введите пакет вручную.");return }
        AlertDialog.Builder(requireContext()).setTitle("Выбор приложения")
            .setItems(applications.map {
                it.loadLabel(requireContext().packageManager).toString()+"\n"+it.activityInfo.packageName
            }.toTypedArray()) { _,index ->
                val app=applications[index]
                packageText.setText(app.activityInfo.packageName)
                labelText.setText(app.loadLabel(requireContext().packageManager).toString())
                show("Выбрано приложение; сохраните профиль.")
            }.setNegativeButton("Отмена",null).show()
    }

    private fun loadPicker() = viewModel.loadPicker()

    private fun showSavedProfiles(profiles: List<SmartProfile>) {
        if (profiles.isEmpty()) { show("Пока нет сохранённых профилей."); return }
        AlertDialog.Builder(requireContext()).setTitle("Сохранённые профили")
            .setItems(profiles.map { it.label + "\n" + it.packageName }.toTypedArray()) { _,index ->
                val p = profiles[index]
                packageText.setText(p.packageName)
                labelText.setText(p.label)
                hotText.setText(p.hotDeciC.toString())
                lowText.setText(p.lowPercent.toString())
                tcpBox.isChecked = p.tcpOptIn
                show("Загружен профиль: " + p.label)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun configureRules() {
        val pkg = packageNameInput()
        if (!SmartProfilesRepository.validatePackage(pkg)) {
            show("Выберите корректный пакет приложения."); return
        }
        viewModel.loadRules(pkg)
    }

    private fun showRulesDialog(pkg: String, rules: List<SmartRule>) {
        if (rules.isEmpty()) { show("Сначала сохраните профиль."); return }
        val flags = rules.map { it.enabled }.toBooleanArray()
        AlertDialog.Builder(requireContext()).setTitle("Правила · " + pkg)
            .setMultiChoiceItems(rules.map {
                it.kind.title + " · " + it.kind.describe(it.threshold)
            }.toTypedArray(), flags) { _,index,checked -> flags[index] = checked }
            .setPositiveButton("Сохранить") { _,_ ->
                viewModel.updateRules(pkg, rules, flags.copyOf())
            }.setNegativeButton("Отмена",null).show()
    }

    private fun openSession() {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) { show("Сначала выберите пакет.");return }
        val args=Bundle().apply {
            putString("smart_package",pkg)
            putString("smart_label",labelText.text.toString().trim().ifBlank { pkg })
            putBoolean("smart_tcp",tcpBox.isChecked)
        }
        val controller=runCatching { NavHostFragment.findNavController(this) }.getOrNull()
        if(controller!=null) controller.navigate(R.id.gameSessionFragment,args)
        else startActivity(Intent(requireContext(),GameSessionActivity::class.java).putExtras(args))
    }

    private fun confirmDelete() {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) { show("Не выбран профиль.");return }
        AlertDialog.Builder(requireContext()).setTitle("Удалить профиль?")
            .setMessage("Профиль $pkg и его правила будут удалены. Старые сессии сохранятся.")
            .setPositiveButton("Удалить") { _,_ ->
                viewModel.delete(pkg)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun insights(days:Int) {
        val pkg=packageNameInput()
        if(!SmartProfilesRepository.validatePackage(pkg)) { show("Сначала выберите пакет.");return }
        viewModel.insights(pkg, days)
    }

    private fun events() = viewModel.events()

    private fun networkProbe() = viewModel.networkProbe()

    private fun setCustomVolume() {
        val edit=EditText(requireContext()).apply {
            hint="0–100"
            inputType=android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(requireContext()).setTitle("Громкость мультимедиа, %")
            .setView(edit).setPositiveButton("Применить") { _,_ ->
                val p=edit.text.toString().toIntOrNull()
                if(p==null || p !in 0..100) show("Укажите процент 0–100.")
                else show(SmartSystemControls.setMediaVolume(requireContext(),p))
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
        if(resultCode!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        when(requestCode) {
            EXPORT -> viewModel.export(uri)
            IMPORT -> AlertDialog.Builder(requireContext()).setTitle("Импортировать профили?")
                .setMessage("Совпадающие профили и их настройки будут перезаписаны. " +
                    "Другие профили и история измерений сохранятся. " +
                    "Файл должен быть экспортом Legion Control версии 1.")
                .setPositiveButton("Импортировать") { _,_ -> viewModel.importBackup(uri) }
                .setNegativeButton("Отмена",null).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pkg", packageText.text.toString())
        outState.putString("label", labelText.text.toString())
        outState.putString("hot", hotText.text.toString())
        outState.putString("low", lowText.text.toString())
        outState.putBoolean("tcp", tcpBox.isChecked)
        super.onSaveInstanceState(outState)
    }
}
