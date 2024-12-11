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

data class ComponentVisit(
    val mountTime: Long,
    val unmountTime: Long? = null,
    val baselineMemory: Int,
    val peakMemory: Int,
    val finalMemory: Int? = null
)

data class ComponentHistory(
    val visits: MutableList<ComponentVisit> = mutableListOf(),
    var totalMounts: Int = 0,
    var currentSnapshot: ComponentMemorySnapshot? = null
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

    private val componentHistory = mutableMapOf<String, ComponentHistory>()
    private val mountedComponents = mutableSetOf<String>()

    // session
    private var sessionStartTime: Long = 0
    private var sessionDurationMs: Long = 0
    private var isSessionActive: Boolean = false

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
            
            val history = componentHistory.getOrPut(componentName) { ComponentHistory() }
            history.totalMounts++
            mountedComponents.add(componentName)
            
            val snapshot = ComponentMemorySnapshot(
                baselineMemory = usedMemory,
                previousMemory = usedMemory,
                peakMemory = usedMemory,
                readings = mutableListOf(usedMemory),
                lastUpdateTime = System.currentTimeMillis()
            )
            
            history.currentSnapshot = snapshot
            componentSnapshots[componentName] = snapshot
            
            history.visits.add(ComponentVisit(
                mountTime = System.currentTimeMillis(),
                baselineMemory = usedMemory,
                peakMemory = usedMemory
            ))
            
            println("LEAKMONITOR:📝 Component $componentName mounted (Visit #${history.totalMounts})")
            "Started tracking $componentName"
        }


        AsyncFunction("stopComponentTracking") { componentName: String ->
            val history = componentHistory[componentName]
            val snapshot = componentSnapshots[componentName]
            
            if (history != null && snapshot != null) {
                val currentVisit = history.visits.lastOrNull()
                if (currentVisit != null) {
                    val updatedVisit = currentVisit.copy(
                        unmountTime = System.currentTimeMillis(),
                        peakMemory = snapshot.peakMemory,
                        finalMemory = snapshot.previousMemory
                    )
                    history.visits[history.visits.lastIndex] = updatedVisit
                    
                    println("LEAKMONITOR:📝 Component $componentName unmounted:")
                    println("LEAKMONITOR:  Visit duration: ${(updatedVisit.unmountTime!! - updatedVisit.mountTime) / 1000}s")
                    println("LEAKMONITOR:  Memory change: ${updatedVisit.finalMemory!! - updatedVisit.baselineMemory}MB")
                }
            }
            
            mountedComponents.remove(componentName)
            componentSnapshots.remove(componentName)
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


        AsyncFunction("startSession") { durationMinutes: Int ->
            isSessionActive = true
            sessionStartTime = System.currentTimeMillis()
            sessionDurationMs = durationMinutes * 60 * 1000L

            // Reset snapshots for new session
            componentSnapshots.clear()
            reportedLeaks.clear()

            println("LEAKMONITOR:📊 Starting ${durationMinutes}min monitoring session")
            // startMemoryMonitoringJob(1000)

            "Session started for $durationMinutes minutes"
        }

        AsyncFunction("stopSession") {
            val results = generateSessionReport()
            stopMemoryMonitoring()
            isSessionActive = false
            println("LEAKMONITOR:📊 Session completed")
            results
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
                    println("LEAKMONITOR:🚨 LEAK DETECTED in component - snapshot: $componentName")
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

    private fun generateSessionReport(): Map<String, Any> {
        println("LEAKMONITOR:📊 Session Analysis Summary:")
        println("LEAKMONITOR:  Total components tracked: ${componentHistory.size}")
        
        val componentAnalysis = componentHistory.map { (name, history) ->
            val visits = history.visits
            val totalMemoryChange = visits.sumOf { visit -> 
                (visit.finalMemory ?: visit.peakMemory) - visit.baselineMemory 
            }
            val avgMemoryPerVisit = totalMemoryChange / visits.size
            val maxSingleVisitIncrease = visits.maxOf { visit ->
                (visit.finalMemory ?: visit.peakMemory) - visit.baselineMemory
            }
            
            println("LEAKMONITOR:  📱 $name:")
            println("LEAKMONITOR:    Total visits: ${history.totalMounts}")
            println("LEAKMONITOR:    Avg memory change per visit: ${avgMemoryPerVisit}MB")
            println("LEAKMONITOR:    Max single visit increase: ${maxSingleVisitIncrease}MB")
            
            mapOf(
                "componentName" to name,
                "totalVisits" to history.totalMounts,
                "totalMemoryChange" to totalMemoryChange,
                "averageMemoryPerVisit" to avgMemoryPerVisit,
                "maxSingleVisitIncrease" to maxSingleVisitIncrease,
                "visits" to visits.map { visit ->
                    mapOf(
                        "mountTime" to visit.mountTime,
                        "unmountTime" to visit.unmountTime,
                        "baselineMemory" to visit.baselineMemory,
                        "peakMemory" to visit.peakMemory,
                        "finalMemory" to visit.finalMemory,
                        "memoryChange" to ((visit.finalMemory ?: visit.peakMemory) - visit.baselineMemory)
                    )
                }
            )
        }.sortedByDescending { it["totalMemoryChange"] as Int }

        println("\nLEAKMONITOR:🚨 Top Memory Consumers:")
        componentAnalysis.take(3).forEach { analysis ->
            println("LEAKMONITOR:  ${analysis["componentName"]}: ${analysis["totalMemoryChange"]}MB total (${analysis["averageMemoryPerVisit"]}MB avg/visit)")
        }

        return mapOf(
            "sessionDuration" to (System.currentTimeMillis() - sessionStartTime),
            "componentsAnalyzed" to componentHistory.size,
            "componentAnalysis" to componentAnalysis,
            "timestamp" to System.currentTimeMillis()
        )
    }



    private fun stopMemoryMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
}