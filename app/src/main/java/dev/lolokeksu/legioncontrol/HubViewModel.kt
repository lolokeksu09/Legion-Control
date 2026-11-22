package dev.lolokeksu.legioncontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface HubLoadState {
    data object Loading : HubLoadState
    data class Ready(val snapshot: HubSnapshot) : HubLoadState
    data class Failed(val message: String) : HubLoadState
}

/**
 * Lifecycle-aware MVVM coordinator. SQLite/PackageManager/MediaStore work happens on
 * one worker, never on main. Fragment recreation does not create a new repository thread.
 */
class HubViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = HubRepository(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val mutableState = MutableLiveData<HubLoadState>(HubLoadState.Loading)
    val state: LiveData<HubLoadState> = mutableState

    private val mutableActionResult = MutableLiveData<String>()
    val actionResult: LiveData<String> = mutableActionResult

    private var latest = HubSnapshot.EMPTY
    fun snapshot(): HubSnapshot = latest

    fun refresh() {
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            try {
                val snapshot = backend.read()
                latest = snapshot
                mutableState.postValue(HubLoadState.Ready(snapshot))
            } catch (e: Exception) {
                mutableState.postValue(HubLoadState.Failed(
                    "Не удалось загрузить Game Hub: " + e.javaClass.simpleName))
            } finally {
                busy.set(false)
            }
        }
    }

    fun toggleFavorite(pkg: String, label: String) {
        worker.execute {
            val action = runCatching { backend.toggleFavorite(pkg, label) }
            action.onSuccess {
                mutableActionResult.postValue(if (it) "Игра добавлена в избранное" else "Убрана из избранного")
            }.onFailure {
                mutableActionResult.postValue("Не удалось сохранить избранное: " +
                    it.javaClass.simpleName)
            }
            runCatching { backend.read() }.onSuccess {
                latest = it
                mutableState.postValue(HubLoadState.Ready(it))
            }.onFailure {
                mutableState.postValue(HubLoadState.Failed(
                    "Не удалось перечитать библиотеку: " + it.javaClass.simpleName))
            }
        }
    }

    fun chooseForToolbox(pkg: String, label: String) {
        worker.execute {
            val result = runCatching { backend.chooseToolboxGame(pkg, label) }
            mutableActionResult.postValue(result.fold(
                { if (it) "Игра выбрана для Game Toolbox" else "Android не позволяет открыть эту игру" },
                { "Не удалось выбрать игру: " + it.javaClass.simpleName }
            ))
        }
    }

    override fun onCleared() {
        worker.shutdown()
        super.onCleared()
    }
}
