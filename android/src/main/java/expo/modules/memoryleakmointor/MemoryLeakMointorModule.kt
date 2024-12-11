package expo.modules.memoryleakmointor

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt
import kotlinx.coroutines.*

data class ComponentMemorySnapshot(
    var baselineMemory: Int = 0,
    var previousMemory: Int = 0,
    var peakMemory: Int = 0,
    var readings: MutableList<Int> = mutableListOf(),
    var lastUpdateTime: Long = 0
)

class MemoryLeakMointorModule : Module() {
    private val componentSnapshots = mutableMapOf<String, ComponentMemorySnapshot>()
    private val reportedLeaks = mutableSetOf<String>() // Track components already reported for leaks
    private val LEAK_WINDOW_SIZE = 8
    private val SIGNIFICANT_INCREASE_MB = 20
    private val MIN_MEMORY_CHANGE = 5
    private val SUSTAINED_INCREASE_THRESHOLD = 4
    private val SYSTEM_MEMORY_THRESHOLD = 50
    private var monitoringJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSystemMemory: Int = 0

    override fun definition() = ModuleDefinition {
        Name("MemoryLeakMointor")

        Constants(
            "MEMORY_UNITS" to "MB",
            "UPDATE_INTERVAL" to 1000,
            "PI" to Math.PI
        )

        Events("onChange", "onMemoryUpdate", "onLeakDetected")

        AsyncFunction("startComponentTracking") { componentName: String ->
            val memoryInfo = getMemoryMetrics()
            val usedMemory = memoryInfo.getValue("usedMemory") as Int
            
            componentSnapshots[componentName] = ComponentMemorySnapshot(
                baselineMemory = usedMemory,
                previousMemory = usedMemory,
                peakMemory = usedMemory,
                readings = mutableListOf(usedMemory),
                lastUpdateTime = System.currentTimeMillis()
            )
            
            "Started tracking $componentName"
        }

        AsyncFunction("stopComponentTracking") { componentName: String ->
            componentSnapshots.remove(componentName)
            reportedLeaks.remove(componentName) // Remove from reported leaks when stopping tracking
            "Stopped tracking $componentName"
        }

        AsyncFunction("resetLeakTracking") {
            reportedLeaks.clear() // Add function to manually reset leak tracking
            "Leak tracking reset"
        }

        AsyncFunction("getMemoryInfo") {
            getMemoryMetrics()
        }

        AsyncFunction("startMemoryMonitoring") { intervalMs: Int ->
            stopMemoryMonitoring()
            startMemoryMonitoringJob(intervalMs)
            "Memory monitoring started"
        }

        AsyncFunction("stopMemoryMonitoring") {
            stopMemoryMonitoring()
            reportedLeaks.clear() // Clear reported leaks when stopping monitoring
            "Memory monitoring stopped"
        }
    }

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

    private fun startMemoryMonitoringJob(intervalMs: Int) {
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    val memoryMetrics = getMemoryMetrics()
                    val usedMemory = memoryMetrics["usedMemory"] as Int
                    
                    componentSnapshots.forEach { (componentName, snapshot) ->
                        checkComponentLeak(componentName, snapshot, usedMemory)
                    }

                    mainHandler.post {
                        sendEvent("onMemoryUpdate", mapOf(
                            "memoryInfo" to memoryMetrics,
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                    
                    delay(intervalMs.toLong())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkComponentLeak(componentName: String, snapshot: ComponentMemorySnapshot, currentMemory: Int) {

    val now = System.currentTimeMillis()

    val memoryIncrease = currentMemory - snapshot.previousMemory
    
    if (Math.abs(memoryIncrease) >= MIN_MEMORY_CHANGE) {
        
        snapshot.readings.add(currentMemory)
        
        if (snapshot.readings.size > LEAK_WINDOW_SIZE) {
            val removed = snapshot.readings.removeAt(0)
        }
        
        snapshot.previousMemory = currentMemory
        snapshot.peakMemory = maxOf(currentMemory, snapshot.peakMemory)
        snapshot.lastUpdateTime = now

        if (snapshot.readings.size >= 3) {
            val totalIncrease = currentMemory - snapshot.readings.first()
            
            val increasingTrend = snapshot.readings.zipWithNext().all { (a, b) -> 
                b >= (a - MIN_MEMORY_CHANGE) 
            }

            if (totalIncrease >= SIGNIFICANT_INCREASE_MB && increasingTrend) {
                println("LEAKMONITOR:🚨 LEAK DETECTED in component: $componentName")
                println("LEAKMONITOR:  Total increase: $totalIncrease MB")
                println("LEAKMONITOR:  Initial memory: ${snapshot.readings.first()} MB")
                println("LEAKMONITOR:  Current memory: $currentMemory MB")
                
                reportedLeaks.add(componentName)
                mainHandler.post {
                    sendEvent("onLeakDetected", mapOf(
                        "componentName" to componentName,
                        "totalIncrease" to totalIncrease,
                        "currentMemory" to currentMemory,
                        "initialMemory" to snapshot.readings.first(),
                        "memoryReadings" to snapshot.readings,
                        "timestamp" to System.currentTimeMillis(),
                        "isFirstReport" to true
                    ))
                }
                println("LEAKMONITOR:📢 Leak event dispatched")
            } else {
            }
        } else {
            // println("LEAKMONITOR:⏳ Waiting for more readings (current: ${snapshot.readings.size}, required: 3)")
        }
    } else {
        // error here 
    }
}


    private fun stopMemoryMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
}