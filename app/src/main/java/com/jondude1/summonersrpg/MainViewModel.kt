package com.jondude1.summonersrpg

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jondude1.summonersrpg.sim.Game
import com.jondude1.summonersrpg.sim.Meta
import com.jondude1.summonersrpg.sim.SaveIo
import com.jondude1.summonersrpg.update.UpdateInfo
import com.jondude1.summonersrpg.update.Updater
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UpdateUi {
    data object Idle : UpdateUi()
    data class Available(val info: UpdateInfo) : UpdateUi()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val saveFile = File(app.filesDir, "run.json")
    private val metaFile = File(app.filesDir, "meta.json")
    private val prefs = app.getSharedPreferences("summoners", Context.MODE_PRIVATE)

    var game: Game = loadGame()
        private set
    val meta: Meta = loadMeta()
    private var recorded = game.phase == "ended"

    private val _gen = MutableStateFlow(0)
    /** Bumps when a new run starts, so the screen rebuilds its art. */
    val gen: StateFlow<Int> = _gen

    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(v) {
            prefs.edit().putFloat("speed", v).apply()
            game.speed = v
        }

    init {
        game.speed = speed
    }

    private fun loadGame(): Game {
        try {
            if (saveFile.exists()) return SaveIo.load(saveFile.readText())
        } catch (_: Exception) {
        }
        return Game(System.currentTimeMillis())
    }

    private fun loadMeta(): Meta = try {
        if (metaFile.exists()) Meta.fromJson(metaFile.readText()) else Meta()
    } catch (_: Exception) {
        Meta()
    }

    /** Called every few seconds from the screen: folds the run's discoveries into the Critterdex. */
    fun merge() {
        meta.seen.addAll(game.seenNow)
        meta.caught.addAll(game.caughtNow)
        if (game.phase == "ended" && !recorded) {
            recorded = true
            meta.legends.add(0, game.legend())
            while (meta.legends.size > 60) meta.legends.removeAt(meta.legends.size - 1)
            saveAll()
        }
    }

    fun saveAll() {
        val runJson = try { SaveIo.save(game) } catch (_: Exception) { null }
        val metaJson = meta.toJson()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (runJson != null) saveFile.writeText(runJson)
                metaFile.writeText(metaJson)
            } catch (_: Exception) {
            }
        }
    }

    /** A new villager steps up to the rift gate. The old run goes into the Hall of Legends if it got anywhere. */
    fun newRun() {
        merge()
        if (!recorded && game.party.isNotEmpty()) {
            val g = game.legend()
            meta.legends.add(0, com.jondude1.summonersrpg.sim.Legend(g.name, g.job, g.persona, g.reached, false, "Left the road in ${g.reached}", g.bound, g.best, g.days, g.level))
        }
        meta.runs++
        game = Game(System.currentTimeMillis())
        game.speed = speed
        recorded = false
        _gen.value = _gen.value + 1
        saveAll()
    }

    // ---- updates ----
    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update
    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus
    private var lastCheck = 0L

    fun onResume() {
        val now = System.currentTimeMillis()
        if (now - lastCheck > 30 * 60 * 1000L) checkForUpdate(quiet = true)
    }

    fun checkForUpdate(quiet: Boolean) {
        lastCheck = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val info = Updater.fetchLatest()
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                    _update.value = UpdateUi.Available(info)
                    _updateStatus.value = null
                } else if (!quiet) {
                    _updateStatus.value = "You're on the latest version (v${BuildConfig.VERSION_NAME})."
                }
            } catch (e: Exception) {
                if (!quiet) _updateStatus.value = "Couldn't check for updates: ${e.message}"
            }
        }
    }

    fun dismissUpdate() {
        _update.value = UpdateUi.Idle
    }

    fun startUpdate() {
        val s = _update.value as? UpdateUi.Available ?: return
        if (!Updater.canInstall(ctx)) {
            _updateStatus.value = "Allow installs from Summoners RPG, then tap Update again."
            Updater.openInstallPermission(ctx)
            return
        }
        viewModelScope.launch {
            try {
                _update.value = UpdateUi.Downloading(s.info, 0f)
                val apk = Updater.download(ctx, s.info) { p -> _update.value = UpdateUi.Downloading(s.info, p) }
                saveAll()
                withContext(Dispatchers.Main) { Updater.install(ctx, apk) }
                _update.value = UpdateUi.Available(s.info)
            } catch (e: Exception) {
                _update.value = UpdateUi.Available(s.info)
                _updateStatus.value = "Download failed: ${e.message}"
            }
        }
    }
}
