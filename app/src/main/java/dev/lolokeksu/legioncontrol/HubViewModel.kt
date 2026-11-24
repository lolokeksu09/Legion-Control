package dev.lolokeksu.legioncontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import android.os.SystemClock

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
    private val refreshGate = HubRefreshGate()
    @Volatile private var closed=false
    private val mutableState = MutableLiveData<HubLoadState>(HubLoadState.Loading)
    val state: LiveData<HubLoadState> = mutableState

    private val mutableActionResult = MutableLiveData<String>()
    val actionResult: LiveData<String> = mutableActionResult

    @Volatile private var latest = HubSnapshot.EMPTY
    private var published:HubSnapshot?=null
    fun snapshot(): HubSnapshot = latest

    fun refresh(force:Boolean=false) {
        if(closed || !refreshGate.request(SystemClock.elapsedRealtime(),force)) return
        worker.execute {
            do {
                val success=readAndPublish()
                val again=refreshGate.complete(SystemClock.elapsedRealtime(),success)
            } while(again && !closed)
        }
    }

    private fun readAndPublish():Boolean {
        if(closed)return false
        return try {
            val snapshot=backend.read()
            if(!closed) {
                latest=snapshot
                if(published!=snapshot) {
                    published=snapshot
                    mutableState.postValue(HubLoadState.Ready(snapshot))
                }
            }
            true
        } catch(e:Exception) {
            published=null
            if(!closed) mutableState.postValue(HubLoadState.Failed(
                "Не удалось загрузить Game Hub: "+e.javaClass.simpleName))
            false
        }
    }

    fun toggleFavorite(pkg: String, label: String) {
        if(closed)return
        worker.execute {
            val action = runCatching { backend.toggleFavorite(pkg, label) }
            action.onSuccess {
                mutableActionResult.postValue(if (it) "Игра добавлена в избранное" else "Убрана из избранного")
            }.onFailure {
                mutableActionResult.postValue("Не удалось сохранить избранное: " +
                    it.javaClass.simpleName)
            }
            readAndPublish()
        }
    }

    fun chooseForToolbox(pkg: String, label: String) {
        if(closed)return
        worker.execute {
            val result = runCatching { backend.chooseToolboxGame(pkg, label) }
            mutableActionResult.postValue(result.fold(
                { if (it) "Игра выбрана для Game Toolbox" else "Android не позволяет открыть эту игру" },
                { "Не удалось выбрать игру: " + it.javaClass.simpleName }
            ))
        }
    }

    override fun onCleared() {
        closed=true
        refreshGate.close()
        worker.shutdown()
        super.onCleared()
    }
}
