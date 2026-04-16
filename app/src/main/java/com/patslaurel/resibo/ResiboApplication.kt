package com.patslaurel.resibo

import android.app.Application
import com.patslaurel.resibo.llm.LlmTriageEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ResiboApplication : Application() {
    @Inject lateinit var engine: LlmTriageEngine

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (engine.defaultModelPath.exists()) {
                engine.warmUp()
            }
        }
    }
}
