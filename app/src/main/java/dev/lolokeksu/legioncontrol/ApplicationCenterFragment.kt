package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
class ApplicationCenterFragment:Fragment() {
    companion object { private const val EXPORT_CSV=2101 }
    private lateinit var viewModel: ApplicationCenterViewModel
    private lateinit var packageText:EditText
    private lateinit var aliasText:EditText
    private lateinit var categoryText:EditText
    private lateinit var noteText:EditText
    private lateinit var favorite:CheckBox
    private lateinit var output:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(18.dp,28.dp,18.dp,32.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(value:String) {
            root.addView(TextView(requireContext()).apply {
                text=value; textSize=20f; setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD)
                setPadding(0,12.dp,0,12.dp)
            })
        }
        fun note(value:String) {
            root.addView(TextView(requireContext()).apply {
                text=value; textSize=13f; setTextColor(0xFFAAAAAA.toInt())
                setPadding(0,6.dp,0,12.dp)
            })
        }
        fun edit(hintLabel:String,multiLine:Boolean=false):EditText=
            EditText(requireContext()).apply {
                hint=hintLabel
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setSingleLine(!multiLine)
                if(multiLine) { minLines=2;maxLines=5 }
                root.addView(this)
            }
        fun button(label:String,action:()->Unit) {
            root.addView(Button(requireContext()).apply {
                text=label
                LegionUiSupport.styleAction(this)
                setOnClickListener { action() }
            })
        }
        title("APPLICATION CONTROL CENTER")
        note("Библиотека игр и приложений Android 13+. "+
            "Обнаруживаются только видимые Android приложения. "+
            "Ни Root/Shizuku, ни изменения чужих разрешений не используются.")
        title("Библиотека приложений")
        button("Выбрать видимое установленное приложение") { chooseApp() }
        button("Найти приложение по названию / пакету") { searchApp() }
        button("Моя библиотека / избранное") { chooseTracked() }
        button("Только избранные приложения") { chooseTracked(onlyFavorites=true) }
        title("Карточка выбранного приложения")
        packageText=edit("Пакет приложения (com.example.game)")
        aliasText=edit("Название в библиотеке")
        categoryText=edit("Категория · до 40 символов")
        noteText=edit("Локальная заметка",true)
        favorite=CheckBox(requireContext()).apply {
            text="В избранное"
            setTextColor(Color.WHITE)
        }
        root.addView(favorite)
        button("СОХРАНИТЬ В БИБЛИОТЕКУ") { save() }
        title("Проверка версий и изменений")
        button("Проверить версию и состояние выбранного") { scanOne() }
        button("Проверить изменения всех отслеживаемых приложений") { scanAll() }
        button("Карточка приложения и видимые разрешения") { inspect() }
        button("Последние события обновлений и видимости") { showEvents() }
        title("Управление записью")
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
            selectedPackage()?.let { openSystem(ApplicationInventory.appInfo(requireContext(),it)) }
        }
        button("Настройки уведомлений выбранного приложения") {
            selectedPackage()?.let { openSystem(ApplicationInventory.notifications(requireContext(),it)) }
        }
        button("Оптимизация батареи · системный список") {
            openSystem(ApplicationInventory.batterySettings())
        }
        button("Открыть Smart Profiles") {
            ModuleNavigation.open(this,SmartProfilesActivity::class.java,Bundle().apply {
                selectedPackage()?.let { putString("app_center_package",it) }
            })
        }
        button("Открыть Experiment Lab для этого пакета") {
            ModuleNavigation.open(this,ExperimentLabActivity::class.java,Bundle().apply {
                selectedPackage()?.let { putString("experiment_package",it) }
            })
        }
        title("Экспорт локальных данных")
        button("СОХРАНИТЬ библиотеку и события в CSV") { exportCsv() }
        note("CSV сохраняется через системное окно выбора файла. "+
            "Примечания могут быть личными — проверьте их перед публикацией. "+
            "Legion Control ничего не выгружает самостоятельно.")
        output=TextView(requireContext()).apply {
            setTextColor(Color.WHITE);textSize=14f
            setLineSpacing(4f,1f)
            setTextIsSelectable(true)
            setPadding(0,16.dp,0,30.dp)
            text="Выберите приложение. Все проверки запускаются только по нажатию."
        }
        root.addView(output)
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        viewModel = ViewModelProvider(this)[ApplicationCenterViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { update ->
            show(update.message)
            update.launchable?.let { apps ->
                viewModel.consumeDialog(update.nonce)
                showVisibleApps(apps)
            }
            update.tracked?.let { entries ->
                viewModel.consumeDialog(update.nonce)
                showTrackedApps(entries)
            }
        }
        if (savedInstanceState != null) {
            packageText.setText(savedInstanceState.getString("pkg").orEmpty())
            aliasText.setText(savedInstanceState.getString("alias").orEmpty())
            categoryText.setText(savedInstanceState.getString("category").orEmpty())
            noteText.setText(savedInstanceState.getString("note").orEmpty())
            favorite.isChecked = savedInstanceState.getBoolean("favorite",false)
        } else arguments?.getString("app_center_package")?.takeIf {
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

    private fun chooseApp() { pickApp(null) }

    private fun searchApp() {
        val input=EditText(requireContext()).apply {
            hint="Поиск в доступном списке приложений"
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext()).setTitle("Поиск").setView(input)
            .setPositiveButton("Найти") { _,_ -> pickApp(input.text.toString().trim()) }
            .setNegativeButton("Отмена",null).show()
    }

    private fun pickApp(filter: String?) = viewModel.pickApp(filter)

    private fun showVisibleApps(found: List<LaunchableApp>) {
        if (found.isEmpty()) { show("Android не вернул видимых приложений по запросу."); return }
        AlertDialog.Builder(requireContext()).setTitle("Приложения · ${found.size}")
            .setItems(found.map { it.label + "\n" + it.packageName }.toTypedArray()) { _,index ->
                packageText.setText(found[index].packageName)
                aliasText.setText(found[index].label)
                categoryText.setText("")
                noteText.setText("")
                favorite.isChecked=false
                show("Выбрано: " + found[index].label +
                    "\nМожете добавить его в библиотеку.")
            }.setNegativeButton("Отмена",null).show()
    }

    private fun chooseTracked(onlyFavorites: Boolean=false) = viewModel.chooseTracked(onlyFavorites)

    private fun showTrackedApps(entries: List<TrackedApplication>) {
        if (entries.isEmpty()) { show("Библиотека пуста — сохраните приложение."); return }
        AlertDialog.Builder(requireContext()).setTitle("Моя библиотека · ${entries.size}")
            .setItems(entries.map {
                (if (it.favorite) "★ " else "") +
                    it.alias.ifBlank { it.packageName } +
                    (if (it.category.isBlank()) "" else " · " + it.category) +
                    "\n" + it.packageName
            }.toTypedArray()) { _,index ->
                val item=entries[index]
                packageText.setText(item.packageName)
                aliasText.setText(item.alias)
                categoryText.setText(item.category)
                noteText.setText(item.note)
                favorite.isChecked=item.favorite
                show("Выбрано: " + item.packageName + "\nНаблюдение: " + item.lastSeenStatus)
            }.setNegativeButton("Отмена",null).show()
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
        viewModel.save(pkg,alias,category,note,selected)
    }

    private fun scanOne() {
        val pkg=selectedPackage() ?: return
        viewModel.scanOne(pkg)
    }

    private fun scanAll() = viewModel.scanAll()

    private fun inspect() {
        val pkg=selectedPackage() ?: return
        viewModel.inspect(pkg)
    }

    private fun showEvents() = viewModel.showEvents()

    private fun confirmRemove() {
        val pkg=selectedPackage() ?: return
        AlertDialog.Builder(requireContext()).setTitle("Удалить из библиотеки?")
            .setMessage("Удалятся только сохранённая заметка, категория, избранное "+
                "и события наблюдения для $pkg. Само приложение и игровые сессии "+
                "останутся нетронутыми.")
            .setPositiveButton("Удалить") { _,_ ->
                viewModel.remove(pkg)
            }.setNegativeButton("Отмена",null).show()
    }

    private fun usage(days:Int) {
        val pkg=selectedPackage() ?: return
        viewModel.usage(pkg,days)
    }

    private fun openSystem(intent:Intent) {
        runCatching { startActivity(intent) }.onFailure {
            show("Системный экран недоступен на этой прошивке: "+
                it.javaClass.simpleName)
        }
    }

    private fun launchApp() {
        val pkg=selectedPackage() ?: return
        val intent=ApplicationInventory.launchIntent(requireContext(),pkg)
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
        if(requestCode!=EXPORT_CSV || resultCode!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        viewModel.exportCsv(uri)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false) // Manual scan stops at screen exit.
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pkg",packageText.text.toString())
        outState.putString("alias",aliasText.text.toString())
        outState.putString("category",categoryText.text.toString())
        outState.putString("note",noteText.text.toString())
        outState.putBoolean("favorite",favorite.isChecked)
        super.onSaveInstanceState(outState)
    }
}
