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
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException

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
    var peakMemory: Int,       
    var finalMemory: Int? = null,
    var memoryReadings: MutableList<Int> = mutableListOf() // Added to track readings
)

data class ComponentHistory(
    val visits: MutableList<ComponentVisit> = mutableListOf(),
    var totalMounts: Int = 0,
    var currentSnapshot: ComponentMemorySnapshot? = null
)

class MemoryLeakMointorModule : Module() {
    private lateinit var hierarchyTracker: ComponentHierarchyTracker
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

         OnCreate {
            // Initialize hierarchy tracker with the correct context
            hierarchyTracker = ComponentHierarchyTracker(appContext.reactContext!!)
        }

        Constants(
            "MEMORY_UNITS" to "MB",
            "UPDATE_INTERVAL" to 1000,
            "PI" to Math.PI
        )

        Events("onChange", "onMemoryUpdate", "onLeakDetected")

      

        // AsyncFunction("startComponentTracking") { componentName: String ->
        //     val memoryInfo = getMemoryMetrics()
        //     val usedMemory = memoryInfo.getValue("usedMemory") as Int
            
        //     val history = componentHistory.getOrPut(componentName) { ComponentHistory() }
        //     history.totalMounts++
        //     mountedComponents.add(componentName)
            
        //     val snapshot = ComponentMemorySnapshot(
        //         baselineMemory = usedMemory,
        //         previousMemory = usedMemory,
        //         peakMemory = usedMemory,
        //         readings = mutableListOf(usedMemory),
        //         lastUpdateTime = System.currentTimeMillis()
        //     )
            
        //     history.currentSnapshot = snapshot
        //     componentSnapshots[componentName] = snapshot
            
        //     val visit = ComponentVisit(
        //         mountTime = System.currentTimeMillis(),
        //         baselineMemory = usedMemory,
        //         peakMemory = usedMemory,
        //         memoryReadings = mutableListOf(usedMemory)
        //     )
            
        //     history.visits.add(visit)
        //     println("LEAKMONITOR:📝 Component $componentName mounted (Visit #${history.totalMounts}) - Initial Memory: $usedMemory MB")
        //     "Started tracking $componentName"
        // }


        
        // AsyncFunction("stopComponentTracking") { componentName: String ->
        //     val history = componentHistory[componentName]
        //     val snapshot = componentSnapshots[componentName]
            
        //     if (history != null && snapshot != null) {
        //         val currentVisit = history.visits.lastOrNull()
        //         if (currentVisit != null) {
        //             val finalMemory = getMemoryMetrics()["usedMemory"] as Int  // Get current memory
        //             val memoryChange = finalMemory - currentVisit.baselineMemory
                    
        //             val updatedVisit = currentVisit.copy(
        //                 unmountTime = System.currentTimeMillis(),
        //                 peakMemory = maxOf(snapshot.peakMemory, finalMemory),  // Use max of peak and final
        //                 finalMemory = finalMemory,
        //                 memoryReadings = currentVisit.memoryReadings  // Keep the readings
        //             )
        //             history.visits[history.visits.lastIndex] = updatedVisit
                    
        //             println("""
        //                 LEAKMONITOR:📝 Component $componentName unmounted:
        //                 LEAKMONITOR:  Visit duration: ${(updatedVisit.unmountTime!! - updatedVisit.mountTime) / 1000}s
        //                 LEAKMONITOR:  Memory change: ${memoryChange}MB
        //                 LEAKMONITOR:  Peak memory: ${updatedVisit.peakMemory}MB
        //                 LEAKMONITOR:  Memory trend: ${updatedVisit.memoryReadings.joinToString(" -> ")}MB
        //             """.trimIndent())
        //         }
        //     }
            
        //     mountedComponents.remove(componentName)
        //     componentSnapshots.remove(componentName)
        //     "Stopped tracking $componentName"
        // }


         AsyncFunction("startComponentTracking") { componentName: String ->
            val memoryInfo = getMemoryMetrics()
            val usedMemory = memoryInfo.getValue("usedMemory") as Int
            
            // Track in hierarchy
            val componentId = hierarchyTracker.trackComponent(componentName)
            
            // Create memory snapshot
            val snapshot = ComponentMemorySnapshot(
                baselineMemory = usedMemory,
                previousMemory = usedMemory,
                peakMemory = usedMemory,
                readings = mutableListOf(usedMemory),
                lastUpdateTime = System.currentTimeMillis()
            )
            
            // Update tracking structures
            componentSnapshots[componentId] = snapshot
            val history = componentHistory.getOrPut(componentName) { ComponentHistory() }
            history.totalMounts++
            mountedComponents.add(componentName)
            
            // Create visit record
            val visit = ComponentVisit(
                mountTime = System.currentTimeMillis(),
                baselineMemory = usedMemory,
                peakMemory = usedMemory,
                memoryReadings = mutableListOf(usedMemory)
            )
            
            history.currentSnapshot = snapshot
            history.visits.add(visit)
            
            // Set memory snapshot in hierarchy
            hierarchyTracker.getNode(componentId)?.memorySnapshot = snapshot
            
            println("""
                LEAKMONITOR:📝 Component $componentName mounted:
                LEAKMONITOR:  ID: $componentId
                LEAKMONITOR:  Visit #${history.totalMounts}
                LEAKMONITOR:  Initial Memory: $usedMemory MB
            """.trimIndent())
            
            componentId
        }

        AsyncFunction("stopComponentTracking") { componentId: String ->
            val node = hierarchyTracker.getNode(componentId)
            val componentName = node?.name ?: return@AsyncFunction "Unknown component"
            
            hierarchyTracker.untrackComponent(componentId)
            
            val history = componentHistory[componentName]
            val snapshot = componentSnapshots[componentId]
            
            if (history != null && snapshot != null) {
                val currentVisit = history.visits.lastOrNull()
                if (currentVisit != null) {
                    val finalMemory = getMemoryMetrics()["usedMemory"] as Int
                    val memoryChange = finalMemory - currentVisit.baselineMemory
                    
                    val updatedVisit = currentVisit.copy(
                        unmountTime = System.currentTimeMillis(),
                        peakMemory = maxOf(snapshot.peakMemory, finalMemory),
                        finalMemory = finalMemory,
                        memoryReadings = currentVisit.memoryReadings
                    )
                    history.visits[history.visits.lastIndex] = updatedVisit
                    
                    println("""
                        LEAKMONITOR:📝 Component $componentName unmounted:
                        LEAKMONITOR:  ID: $componentId
                        LEAKMONITOR:  Visit duration: ${(updatedVisit.unmountTime!! - updatedVisit.mountTime) / 1000}s
                        LEAKMONITOR:  Memory change: ${memoryChange}MB
                        LEAKMONITOR:  Peak memory: ${updatedVisit.peakMemory}MB
                        LEAKMONITOR:  Memory trend: ${updatedVisit.memoryReadings.joinToString(" -> ")}MB
                    """.trimIndent())
                }
            }
            
            mountedComponents.remove(componentName)
            componentSnapshots.remove(componentId)
            
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

            // Reset state for new session
            componentSnapshots.clear()
            reportedLeaks.clear()
            
            println("LEAKMONITOR:📊 Starting ${durationMinutes}min monitoring session")
            startMemoryMonitoringJob(1000)  // Start monitoring immediately
            
            "Session started for $durationMinutes minutes"
        }

        AsyncFunction("stopSession") {
            val results = generateSessionReport()
            stopMemoryMonitoring()  // This cancels the monitoring coroutine
            isSessionActive = false
            componentSnapshots.clear()
            reportedLeaks.clear()
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
            var lastMemoryReading = 0
            
            while (isActive && isSessionActive) {  // Add isSessionActive check
                try {
                    val memoryMetrics = getMemoryMetrics()
                    val usedMemory = memoryMetrics["usedMemory"] as Int
                    
                    // Only check for leaks if memory has changed significantly
                    if (Math.abs(usedMemory - lastMemoryReading) >= MIN_MEMORY_CHANGE) {
                        componentSnapshots.forEach { (componentName, snapshot) ->
                            checkComponentLeak(componentName, snapshot, usedMemory)
                        }
                        lastMemoryReading = usedMemory
                    }

                    // Send memory updates less frequently to reduce noise
                    mainHandler.post {
                        sendEvent("onMemoryUpdate", mapOf(
                            "memoryInfo" to memoryMetrics,
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                    
                    delay(intervalMs.toLong())
                } catch (e: Exception) {
                    println("LEAKMONITOR:❌ Error in monitoring job: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkComponentLeak(componentName: String, snapshot: ComponentMemorySnapshot, currentMemory: Int) {

        if (!isSessionActive) return
    
        val now = System.currentTimeMillis()
        val memoryIncrease = currentMemory - snapshot.previousMemory
        
        if (Math.abs(memoryIncrease) >= MIN_MEMORY_CHANGE) {
            // Update snapshot readings
            snapshot.readings.add(currentMemory)
            if (snapshot.readings.size > LEAK_WINDOW_SIZE) {
                snapshot.readings.removeAt(0)
            }
            
            // Update visit readings
            componentHistory[componentName]?.let { history ->
                history.visits.lastOrNull()?.let { visit ->
                    visit.memoryReadings.add(currentMemory)
                    visit.peakMemory = maxOf(visit.peakMemory, currentMemory)
                    // Update the final memory as we go
                    visit.finalMemory = currentMemory
                }
            }
            
            snapshot.previousMemory = currentMemory
            snapshot.peakMemory = maxOf(currentMemory, snapshot.peakMemory)
            snapshot.lastUpdateTime = now

            println("""
                LEAKMONITOR:📈 Memory update for $componentName:
                LEAKMONITOR:  Current memory: $currentMemory MB
                LEAKMONITOR:  Memory increase: $memoryIncrease MB
            """.trimIndent())

            // Leak detection logic
            if (snapshot.readings.size >= 3 && !reportedLeaks.contains(componentName)) {
                val totalIncrease = currentMemory - snapshot.readings.first()
                
                val increasingTrend = snapshot.readings
                    .zipWithNext()
                    .all { (a, b) -> b >= (a - MIN_MEMORY_CHANGE) }

                if (totalIncrease >= SIGNIFICANT_INCREASE_MB && increasingTrend) {
                    println("""
                        LEAKMONITOR:🚨 LEAK DETECTED in component: $componentName
                        LEAKMONITOR:  Total increase: $totalIncrease MB
                        LEAKMONITOR:  Initial memory: ${snapshot.readings.first()} MB
                        LEAKMONITOR:  Current memory: $currentMemory MB
                        LEAKMONITOR:  Memory trend: ${snapshot.readings.joinToString(" -> ")} MB
                    """.trimIndent())
                
                    reportedLeaks.add(componentName)
                    
                    mainHandler.post {
                        sendEvent("onLeakDetected", mapOf(
                            "componentName" to componentName,
                            "totalIncrease" to totalIncrease,
                            "currentMemory" to currentMemory,
                            "initialMemory" to snapshot.readings.first(),
                            "memoryReadings" to snapshot.readings,
                            "timestamp" to now,
                            "isFirstReport" to true
                        ))
                    }
                }
            }
        }
    }

    // private fun generateSessionReport(): Map<String, Any> {
    //     println("LEAKMONITOR:📊 Session Analysis Summary:")
    //     println("LEAKMONITOR:  Total components tracked: ${componentHistory.size}")
        
    //     val componentAnalysis = componentHistory.map { (name, history) ->
    //         val visits = history.visits
    //         val totalMemoryChange = visits.sumOf { visit -> 
    //             visit.finalMemory?.let { finalMem ->
    //                 finalMem - visit.baselineMemory
    //             } ?: (visit.peakMemory - visit.baselineMemory)
    //         }
            
    //         val avgMemoryPerVisit = if (visits.isNotEmpty()) totalMemoryChange / visits.size else 0
    //         val maxSingleVisitIncrease = visits.maxOfOrNull { visit ->
    //             visit.finalMemory?.let { finalMem ->
    //                 finalMem - visit.baselineMemory
    //             } ?: (visit.peakMemory - visit.baselineMemory)
    //         } ?: 0
            
    //         val initialMemory = visits.firstOrNull()?.baselineMemory ?: 0
    //         val finalMemory = visits.lastOrNull()?.let { lastVisit ->
    //             lastVisit.finalMemory ?: lastVisit.peakMemory
    //         } ?: 0
            
    //         println("""
    //             LEAKMONITOR:  📱 $name:
    //             LEAKMONITOR:    Total visits: ${history.totalMounts}
    //             LEAKMONITOR:    Total memory change: ${totalMemoryChange}MB
    //             LEAKMONITOR:    Inital memory: ${initialMemory}MB
    //             LEAKMONITOR:    Final memory: ${finalMemory}MB
    //         """.trimIndent())
            
    //         mapOf(
    //             "componentName" to name,
    //             "totalVisits" to history.totalMounts,
    //             "totalMemoryChange" to totalMemoryChange,
    //             "averageMemoryPerVisit" to avgMemoryPerVisit,
    //             "maxSingleVisitIncrease" to maxSingleVisitIncrease,
    //             "visits" to visits.map { visit ->
    //                 mapOf(
    //                     "mountTime" to visit.mountTime,
    //                     "unmountTime" to visit.unmountTime,
    //                     "baselineMemory" to visit.baselineMemory,
    //                     "peakMemory" to visit.peakMemory,
    //                     "finalMemory" to visit.finalMemory,
    //                     "memoryChange" to (visit.finalMemory?.minus(visit.baselineMemory) 
    //                         ?: (visit.peakMemory - visit.baselineMemory)),
    //                     "readings" to visit.memoryReadings
    //                 )
    //             }
    //         )
    //     }.sortedByDescending { it["totalMemoryChange"] as Int }

    //     return mapOf(
    //         "sessionDuration" to (System.currentTimeMillis() - sessionStartTime),
    //         "componentsAnalyzed" to componentHistory.size,
    //         "componentAnalysis" to componentAnalysis,
    //         "timestamp" to System.currentTimeMillis()
    //     )
    // }

    private fun generateSessionReport(): Map<String, Any> {
        println("LEAKMONITOR:📊 Session Analysis Summary:")
        println("LEAKMONITOR:  Total components tracked: ${componentHistory.size}")
        
        // Get hierarchy report
        val hierarchyReport = hierarchyTracker.generateHierarchyReport()
        
        // Print the tree structure
        println("\nLEAKMONITOR:📊 Component Hierarchy:")
        fun printTree(node: Map<String, Any>, depth: Int = 0) {
            val indent = "  ".repeat(depth)
            val name = node["name"] as String
            val memoryData = node["memoryData"] as? Map<String, Any>
            val memoryInfo = memoryData?.let { mem ->
                " (Base: ${mem["baseline"]}MB, Peak: ${mem["peak"]}MB, Final: ${mem["final"]}MB)"
            } ?: ""
            
            println("LEAKMONITOR:  $indent├─ $name$memoryInfo")
            
            @Suppress("UNCHECKED_CAST")
            (node["children"] as? List<Map<String, Any>>)?.forEach { child ->
                printTree(child, depth + 1)
            }
        }
        
        // Print each root node
        (hierarchyReport["tree"] as? List<Map<String, Any>>)?.forEach { root ->
            printTree(root)
    }
    
        
        // Generate component analysis
        val componentAnalysis = componentHistory.map { (name, history) ->
            val visits = history.visits
            val totalMemoryChange = visits.sumOf { visit -> 
                visit.finalMemory?.let { finalMem ->
                    finalMem - visit.baselineMemory
                } ?: (visit.peakMemory - visit.baselineMemory)
            }
            
            val avgMemoryPerVisit = if (visits.isNotEmpty()) totalMemoryChange / visits.size else 0
            val maxSingleVisitIncrease = visits.maxOfOrNull { visit ->
                visit.finalMemory?.let { finalMem ->
                    finalMem - visit.baselineMemory
                } ?: (visit.peakMemory - visit.baselineMemory)
            } ?: 0
            
            val initialMemory = visits.firstOrNull()?.baselineMemory ?: 0
            val finalMemory = visits.lastOrNull()?.let { lastVisit ->
                lastVisit.finalMemory ?: lastVisit.peakMemory
            } ?: 0
            
            println("""
                LEAKMONITOR:  📱 $name:
                LEAKMONITOR:    Total visits: ${history.totalMounts}
                LEAKMONITOR:    Total memory change: ${totalMemoryChange}MB
                LEAKMONITOR:    Initial memory: ${initialMemory}MB
                LEAKMONITOR:    Final memory: ${finalMemory}MB
            """.trimIndent())
            
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
                        "memoryChange" to (visit.finalMemory?.minus(visit.baselineMemory) 
                            ?: (visit.peakMemory - visit.baselineMemory)),
                        "readings" to visit.memoryReadings
                    )
                }
            )
        }.sortedByDescending { it["totalMemoryChange"] as Int }

        // Combine everything into final report
        return mapOf(
            "sessionDuration" to (System.currentTimeMillis() - sessionStartTime),
            "componentsAnalyzed" to componentHistory.size,
            "componentAnalysis" to componentAnalysis,
            "componentTree" to (hierarchyReport["tree"] ?: emptyList<Map<String, Any>>()),
            "metrics" to (hierarchyReport["metrics"] ?: emptyMap<String, Any>()),
            "leakSuspects" to (hierarchyReport["leakingSuspects"] ?: emptyList<Map<String, Any>>()),
            "timestamp" to System.currentTimeMillis()
        )
    }

    companion object {
        private const val SIGNIFICANT_INCREASE_MB = 20
    }


    private fun stopMemoryMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
}