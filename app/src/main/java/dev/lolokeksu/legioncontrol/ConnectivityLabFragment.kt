package dev.lolokeksu.legioncontrol

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Visible-only, opt-in public endpoint probes. No foreground service or hidden scheduler. */
class ConnectivityLabFragment : Fragment() {
    companion object {
        private const val EXPORT_CSV = 2001
        private const val INTERVAL_MS = 12_000L
    }

    private lateinit var viewModel:ConnectivityLabViewModel
    private var testing=false
    private lateinit var hostButton:Button
    private lateinit var singleButton:Button
    private lateinit var stabilityButton:Button
    private lateinit var stopButton:Button
    private lateinit var resultView:TextView
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,savedInstanceState:Bundle?):View {
        val root=LinearLayout(requireContext()).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(20.dp,30.dp,20.dp,40.dp)
            setBackgroundColor(Color.BLACK)
        }
        fun title(value:String) {
            root.addView(TextView(requireContext()).apply {
                text=value
                textSize=22f
                setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD)
                setPadding(0,8.dp,0,14.dp)
            })
        }
        fun description(value:String) {
            root.addView(TextView(requireContext()).apply {
                text=value
                textSize=13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0,6.dp,0,14.dp)
                setLineSpacing(3f,1f)
            })
        }
        fun button(textValue:String,featureId:String?=null,onClick:()->Unit):Button {
            return Button(requireContext()).apply {
                if(featureId!=null) FeatureReveal.bind(this,featureId)
                text=textValue
                minHeight=50.dp
                isAllCaps=false
                textSize=14f
                setTextColor(Color.WHITE)
                background=GradientDrawable().apply {
                    setColor(0xFF1E1E21.toInt())
                    cornerRadius=14.dp.toFloat()
                    setStroke(1.dp,0xFF38383E.toInt())
                }
                setPadding(12.dp,7.dp,12.dp,7.dp)
                setOnClickListener { onClick() }
                val p=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin=9.dp
                root.addView(this,p)
            }
        }
        title("CONNECTIVITY OBSERVATORY")
        description("Legion Control · диагностика сети Android 13+. "+
            "Все проверки добровольные и обращаются к публичному серверу: "+
            "это НЕ ping игрового сервера, НЕ FPS и НЕ ICMP.")
        resultView=TextView(requireContext()).apply {
            textSize=14f
            setTextColor(Color.WHITE)
            setLineSpacing(4f,1f)
            setPadding(0,14.dp,0,28.dp)
            text="Сервер диагностики выбран. Чтобы проверить сеть, нажмите замер. "+
                "Обращение к публичному узлу начнётся только после нажатия."
        }
        root.addView(resultView)
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ПРОВЕРКА СОЕДИНЕНИЯ"))
        hostButton=button("Сервер: Cloudflare · one.one.one.one") { chooseHost() }
        singleButton=button("ОДИН ЗАМЕР · DNS / IPv4 / IPv6 / TCP / TLS", "network.single") { startTest(1) }
        stabilityButton=button("ТЕСТ СТАБИЛЬНОСТИ · до 6 циклов (~60 секунд)", "network.stability") { startTest(6) }
        stopButton=button("ОСТАНОВИТЬ текущий тест") { cancelTest(true) }
        stopButton.isEnabled=false
        description("Каждый цикл: DNS, до одного соединения IPv4 и IPv6, "+
            "TCP connect и TLS handshake с проверкой сертификата и имени сайта. "+
            "DNS-кэш Android может влиять на результаты. "+
            "При уходе с экрана запись отменяется; фоновый сервис не запускается.")
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ИСТОРИЯ И СРАВНЕНИЕ"))
        button("История проверок · выбрать запись", "network.history") { showHistory() }
        button("Подробный отчёт выбранной записи") { showSelected() }
        button("Сравнить два последних запуска одного сервера") { compare() }
        button("Сводка по этому серверу за 7 дней") { weekReport() }
        root.addView(LegionUiSupport.sectionLabel(requireContext(), "ЭКСПОРТ И УПРАВЛЕНИЕ"))
        button("Экспорт ВСЕЙ истории сетевых тестов CSV") { exportCsv() }
        button("Поделиться выбранным отчётом") { share() }
        button("Удалить выбранную запись (с подтверждением)") { confirmDelete() }
        description("История и сравнения остаются локальными. CSV сохраняется только "+
            "через системный выбор документа. DNS/TCP/TLS-результаты не доказывают "+
            "качество подключения к конкретной игре. Новые Android разрешения не нужны.")
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        super.onViewCreated(view,savedInstanceState)
        LegionUiSupport.styleModuleTree(view)
        FeatureReveal.fromFragment(this, view, FeatureModule.NETWORK)
        viewModel=ViewModelProvider(this)[ConnectivityLabViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { ui ->
            testing=ui.testing
            updateButtons()
            show(ui.message)
            ui.runs?.let { entries ->
                viewModel.consumeDialog(ui.nonce)
                selectHistory(entries)
            }
            ui.shareText?.let { report ->
                viewModel.consumeDialog(ui.nonce)
                runCatching {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/plain"
                        putExtra(Intent.EXTRA_TEXT,report)
                    },"Поделиться сетевым отчётом"))
                }.onFailure { show("Не удалось открыть меню отправки.") }
            }
        }
        if(savedInstanceState!=null) viewModel.chooseHost(savedInstanceState.getString("host","one.one.one.one"))
        hostButton.text="Сервер: "+(when(viewModel.host) {
            "dns.google" -> "Google DNS · dns.google"
            "dns.quad9.net" -> "Quad9 · dns.quad9.net"
            else -> "Cloudflare · one.one.one.one"
        })
        updateButtons()
    }

    private fun updateButtons() {
        if(::singleButton.isInitialized) {
            singleButton.isEnabled=!testing
            stabilityButton.isEnabled=!testing
            hostButton.isEnabled=!testing
            stopButton.isEnabled=testing
        }
    }

    private fun show(text:String) {
        if(::resultView.isInitialized) resultView.text=text
    }

    private fun chooseHost() {
        if(testing) return
        val names=arrayOf(
            "Cloudflare · one.one.one.one",
            "Google DNS · dns.google",
            "Quad9 · dns.quad9.net"
        )
        LegionDialogs.builder(requireContext()).setTitle("Публичный сервер для диагностики")
            .setItems(names) { _,index ->
                viewModel.chooseHost(ConnectivityProbe.targets[index])
                hostButton.text="Сервер: "+names[index]
                show("Выбран "+viewModel.host+":443. Это внешний тестовый сервер, не игровой хост.")
            }.setNegativeButton("Отмена",null).show()
    }

    private fun startTest(cycles:Int) = viewModel.startTest(cycles)

    private fun cancelTest(display:Boolean) {
        viewModel.cancelTest()
        if(display && ::resultView.isInitialized)
            show("Отмена запрошена. Уже начатое DNS-разрешение может завершиться позже; "+
                "дальнейшие циклы запускаться не будут.")
    }

    private fun showHistory() = viewModel.showHistory()

    private fun selectHistory(items:List<ConnectivityRun>) {
        if(items.isEmpty()) { show("Для "+viewModel.host+" пока нет сохранённых запусков.");return }
        LegionDialogs.builder(requireContext()).setTitle("История · "+viewModel.host)
            .setItems(items.map { r ->
                DateFormat.getDateTimeInstance().format(Date(r.startedAtMs))+
                    " · "+r.mode+" · "+r.status
            }.toTypedArray()) { _,index ->
                viewModel.selectRun(items[index].id)
                show("Выбрана запись "+items[index].id+"\nНажмите «Подробный отчёт».")
            }.setNegativeButton("Отмена",null).show()
    }

    private fun showSelected() {
        val id=viewModel.selectedRunId ?: return show("Сначала выберите тест из истории.")
        viewModel.showSelected(id)
    }

    private fun compare() = viewModel.compare()

    private fun weekReport() = viewModel.weekReport()

    private fun exportCsv() {
        if(testing) return show("Остановите тест перед экспортом.")
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="text/csv"
                putExtra(Intent.EXTRA_TITLE,"legion-network-history-alpha20.csv")
            },EXPORT_CSV)
        }.onFailure { show("Документ не открыт: "+it.javaClass.simpleName) }
    }

    private fun share() {
        val id=viewModel.selectedRunId ?: return show("Выберите запись для передачи.")
        viewModel.share(id)
    }

    private fun confirmDelete() {
        val id=viewModel.selectedRunId ?: return show("Сначала выберите запись.")
        if(testing) return show("Остановите тест перед удалением.")
        LegionDialogs.builder(requireContext()).setTitle("Удалить эту запись?")
            .setMessage("Удалятся только этот сетевой тест и его DNS/TCP/TLS замеры. "+
                "Другие записи, игровые сессии и профили сохранятся.")
            .setDestructive().setPositiveButton("Удалить") { _,_ ->
                viewModel.delete(id)
            }.setNegativeButton("Отмена",null).show()
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=EXPORT_CSV || resultCode!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        viewModel.exportCsv(uri)
    }

    override fun onStop() {
        cancelTest(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("host",viewModel.host)
        super.onSaveInstanceState(outState)
    }
}
