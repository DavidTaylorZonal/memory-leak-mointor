package expo.modules.memoryleakmointor

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import kotlin.math.roundToInt

class MemoryLeakMointorModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("MemoryLeakMointor")

        Constants(
            "MEMORY_UNITS" to "MB",
            "UPDATE_INTERVAL" to 1000,
            "PI" to Math.PI
        )

        Events("onChange", "onMemoryUpdate")

        Function("hello") {
            "Hello world! 👋"
        }

        AsyncFunction("setValueAsync") { value: String ->
            sendEvent("onChange", mapOf(
                "value" to value
            ))
        }

        AsyncFunction("getMemoryInfo") {
            val activityManager = appContext.reactContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: throw Exception("Could not get ActivityManager")
                
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val pid = Process.myPid()
            val pids = intArrayOf(pid)
            val processMemoryInfo = activityManager.getProcessMemoryInfo(pids)
            
            val totalMemoryMB = (memoryInfo.totalMem / 1024.0 / 1024.0).roundToInt()
            val availMemoryMB = (memoryInfo.availMem / 1024.0 / 1024.0).roundToInt()
            val usedMemoryMB = totalMemoryMB - availMemoryMB
            val appMemoryMB = (processMemoryInfo[0].totalPss / 1024.0).roundToInt()

            mapOf(
                "totalMemory" to totalMemoryMB,
                "availableMemory" to availMemoryMB,
                "usedMemory" to usedMemoryMB,
                "appMemory" to appMemoryMB,
                "isLowMemory" to memoryInfo.lowMemory,
                "lowMemoryThreshold" to (memoryInfo.threshold / 1024.0 / 1024.0).roundToInt()
            )
        }

        AsyncFunction("startMemoryMonitoring") { intervalMs: Int ->
            startMemoryUpdates(intervalMs)
            "Memory monitoring started"
        }

        AsyncFunction("stopMemoryMonitoring") {
            stopMemoryUpdates()
            "Memory monitoring stopped"
        }
    }

    private var memoryUpdateJob: java.util.Timer? = null

    private fun getMemoryMetrics(): Map<String, Any> {
        val activityManager = appContext.reactContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: throw Exception("Could not get ActivityManager")
            
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val pid = Process.myPid()
        val pids = intArrayOf(pid)
        val processMemoryInfo = activityManager.getProcessMemoryInfo(pids)
        
        val totalMemoryMB = (memoryInfo.totalMem / 1024.0 / 1024.0).roundToInt()
        val availMemoryMB = (memoryInfo.availMem / 1024.0 / 1024.0).roundToInt()
        val usedMemoryMB = totalMemoryMB - availMemoryMB
        val appMemoryMB = (processMemoryInfo[0].totalPss / 1024.0).roundToInt()

        return mapOf(
            "totalMemory" to totalMemoryMB,
            "availableMemory" to availMemoryMB,
            "usedMemory" to usedMemoryMB,
            "appMemory" to appMemoryMB,
            "isLowMemory" to memoryInfo.lowMemory,
            "lowMemoryThreshold" to (memoryInfo.threshold / 1024.0 / 1024.0).roundToInt()
        )
    }

    private fun startMemoryUpdates(intervalMs: Int) {
        stopMemoryUpdates()

        memoryUpdateJob = java.util.Timer().apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    try {
                        val memoryMetrics = getMemoryMetrics()
                        sendEvent("onMemoryUpdate", mapOf(
                            "memoryInfo" to memoryMetrics,
                            "timestamp" to System.currentTimeMillis()
                        ))
                    } catch (e: Exception) {
                        // Handle or log error
                    }
                }
            }, 0, intervalMs.toLong())
        }
    }

    private fun stopMemoryUpdates() {
        memoryUpdateJob?.cancel()
        memoryUpdateJob = null
    }
}