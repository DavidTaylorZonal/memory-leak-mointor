package expo.modules.memoryleakmointor

import android.content.Context
import expo.modules.kotlin.Promise

data class ComponentNode(
    val id: String,
    val name: String,
    val parentId: String?,
    val mountTime: Long,
    var unmountTime: Long? = null,
    val children: MutableList<String> = mutableListOf(),
    var memorySnapshot: ComponentMemorySnapshot? = null,
    val isRevisit: Boolean = false
)

data class ComponentHierarchy(
    val nodes: MutableMap<String, ComponentNode> = mutableMapOf(),
    val rootIds: MutableSet<String> = mutableSetOf()
)

class ComponentHierarchyTracker(private val context: Context) {
    private val hierarchy = ComponentHierarchy()
    private val parentStack = mutableListOf<String>()
    private val componentNameToIds = mutableMapOf<String, MutableSet<String>>()  // Track all IDs for a component name
    
    fun getNode(componentId: String): ComponentNode? {
        return hierarchy.nodes[componentId]
    }
    
   fun trackComponent(componentName: String): String {
        val componentId = generateComponentId(componentName)
        val parentId = parentStack.lastOrNull()
        
        // Track this ID for the component name
        val componentIds = componentNameToIds.getOrPut(componentName) { mutableSetOf() }
        val isRevisit = componentIds.isNotEmpty()
        componentIds.add(componentId)
        
        val node = ComponentNode(
            id = componentId,
            name = componentName,
            parentId = parentId,
            mountTime = System.currentTimeMillis(),
            isRevisit = isRevisit
        )
        
        hierarchy.nodes[componentId] = node
        
        if (parentId != null) {
            hierarchy.nodes[parentId]?.children?.add(componentId)
        } else {
            hierarchy.rootIds.add(componentId)
        }
        
        parentStack.add(componentId)
        return componentId
    }

    private fun buildTreeNode(nodeId: String): Map<String, Any> {
        val node = hierarchy.nodes[nodeId] ?: return emptyMap()
        val componentIds = componentNameToIds[node.name] ?: emptySet()
        
        val nodeData = mutableMapOf<String, Any>(
            "id" to node.id,
            "name" to node.name,
            "mountTime" to node.mountTime,
            "isRevisit" to node.isRevisit
        )
        
        node.unmountTime?.let { nodeData["unmountTime"] = it }
        node.memorySnapshot?.let { snapshot ->
            nodeData["memoryData"] = mapOf(
                "baseline" to snapshot.baselineMemory,
                "peak" to snapshot.peakMemory,
                "final" to snapshot.previousMemory,
                "readings" to snapshot.readings
            )
        }
        
        // Only include children for non-revisit nodes
        if (!node.isRevisit && node.children.isNotEmpty()) {
            nodeData["children"] = node.children
                .map { childId -> buildTreeNode(childId) }
                .filter { it.isNotEmpty() }
        }
        
        return nodeData
    }
    
    fun untrackComponent(componentId: String) {
        val node = hierarchy.nodes[componentId] ?: return
        node.unmountTime = System.currentTimeMillis()
        parentStack.remove(componentId)
    }
    
    fun generateHierarchyReport(): Map<String, Any> {
        // First, get all components by name to track first occurrence
        val componentFirstOccurrence = mutableMapOf<String, String>() // name -> first id
        hierarchy.nodes.forEach { (id, node) ->
            if (!componentFirstOccurrence.containsKey(node.name)) {
                componentFirstOccurrence[node.name] = id
            }
        }

        // Build tree starting only from true root components
        val rootComponents = hierarchy.rootIds.filter { id ->
            val node = hierarchy.nodes[id]!!
            val isFirstOccurrence = componentFirstOccurrence[node.name] == id
            isFirstOccurrence && node.parentId == null
        }

        val treeStructure = rootComponents
            .sortedBy { hierarchy.nodes[it]?.mountTime ?: 0L }
            .map { rootId -> buildTreeNode(rootId) }

        val metrics = calculateHierarchyMetrics()
        val leakingSuspects = findLeakingSuspects()
        
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
        
        // Print the tree structure
        treeStructure.forEach { root ->
            printTree(root)
        }

        // Print any revisits separately
        hierarchy.nodes.values
            .filter { node -> componentFirstOccurrence[node.name] != node.id }
            .sortedBy { it.mountTime }
            .forEach { node ->
                val memoryInfo = node.memorySnapshot?.let { mem ->
                    " (Base: ${mem.baselineMemory}MB, Peak: ${mem.peakMemory}MB, Final: ${mem.previousMemory}MB)"
                } ?: ""
                println("LEAKMONITOR:  ├─ ${node.name}$memoryInfo (revisit)")
            }
        
        return mapOf(
            "tree" to treeStructure,
            "metrics" to metrics,
            "leakingSuspects" to leakingSuspects,
            "timestamp" to System.currentTimeMillis()
        )
    }

    private fun findLeakingSuspects(): List<Map<String, Any>> {
        val suspects = mutableListOf<Map<String, Any>>()
        
        fun checkNode(nodeId: String, path: List<String>) {
            val node = hierarchy.nodes[nodeId] ?: return
            val currentPath = path + node.name
            
            node.memorySnapshot?.let { snapshot ->
                val memoryChange = snapshot.previousMemory - snapshot.baselineMemory
                if (memoryChange > SIGNIFICANT_INCREASE_MB) {
                    suspects.add(mapOf(
                        "componentId" to node.id,
                        "componentName" to node.name,
                        "memoryIncrease" to memoryChange,
                        "componentPath" to currentPath
                    ))
                }
            }
            
            node.children.forEach { childId ->
                checkNode(childId, currentPath)
            }
        }
        
        hierarchy.rootIds.forEach { rootId ->
            checkNode(rootId, emptyList())
        }
        
        return suspects.sortedByDescending { it["memoryIncrease"] as Int }
    }
    
    private fun calculateHierarchyMetrics(): Map<String, Any> {
        var totalComponents = 0
        var maxDepth = 0
        
        fun traverseTree(nodeId: String, depth: Int) {
            totalComponents++
            maxDepth = maxOf(maxDepth, depth)
            
            val node = hierarchy.nodes[nodeId] ?: return
            node.children.forEach { childId ->
                traverseTree(childId, depth + 1)
            }
        }
        
        hierarchy.rootIds.forEach { rootId ->
            traverseTree(rootId, 0)
        }
        
        return mapOf(
            "totalComponents" to totalComponents,
            "maxDepth" to maxDepth
        )
    }
    
    private fun generateComponentId(componentName: String): String {
        return "${componentName}_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
    
    companion object {
        private const val SIGNIFICANT_INCREASE_MB = 20
    }
}