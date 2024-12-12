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
        // Build tree structure starting with first occurrence of root components
        val rootComponents = hierarchy.rootIds
            .filter { id -> 
                val node = hierarchy.nodes[id]!!
                !node.isRevisit && node.parentId == null
            }
            .sortedBy { hierarchy.nodes[it]?.mountTime ?: 0L }

        val treeStructure = rootComponents.map { buildTreeNode(it) }
        val leakingSuspects = findLeakingSuspects()
        val metrics = calculateHierarchyMetrics()
        
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

        // Print memory leak summary
        if (leakingSuspects.isNotEmpty()) {
            println("\nLEAKMONITOR:🚨 Memory Leak Summary:")
            leakingSuspects.forEach { suspect ->
                val path = suspect["componentPath"] as List<String>
                val increase = suspect["memoryIncrease"] as Int
                val totalIncrease = suspect["totalIncrease"] as Int
                val childrenIncrease = suspect["childrenIncrease"] as Int
                println("""
                    LEAKMONITOR:  Potential memory leak detected in: ${suspect["componentName"]}
                    LEAKMONITOR:    Memory increase: ${increase}MB (Total: ${totalIncrease}MB, Children: ${childrenIncrease}MB)
                    LEAKMONITOR:    Component path: ${path.joinToString(" → ")}
                """.trimIndent())
            }
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
        val processedComponents = mutableSetOf<String>()
        
        // First, analyze leaf nodes (components with no children) first
        fun analyzeNode(nodeId: String, path: List<String>): Int? {
            val node = hierarchy.nodes[nodeId] ?: return null
            val currentPath = path + node.name
            
            // Get memory changes from children first
            var childrenMaxIncrease = 0
            node.children.forEach { childId ->
                val childIncrease = analyzeNode(childId, currentPath) ?: 0
                childrenMaxIncrease = maxOf(childrenMaxIncrease, childIncrease)
            }

            // Calculate this node's memory increase
            val nodeIncrease = node.memorySnapshot?.let { snapshot ->
                snapshot.previousMemory - snapshot.baselineMemory
            } ?: 0

            // Only consider this node leaking if its increase is significantly more than its children
            val realIncrease = nodeIncrease - childrenMaxIncrease
            if (realIncrease > SIGNIFICANT_INCREASE_MB && !processedComponents.contains(node.name)) {
                suspects.add(mapOf(
                    "componentId" to node.id,
                    "componentName" to node.name,
                    "memoryIncrease" to realIncrease,
                    "totalIncrease" to nodeIncrease,
                    "childrenIncrease" to childrenMaxIncrease,
                    "componentPath" to currentPath,
                    "baseline" to (node.memorySnapshot?.baselineMemory ?: 0),
                    "peak" to (node.memorySnapshot?.peakMemory ?: 0),
                    "final" to (node.memorySnapshot?.previousMemory ?: 0)
                ))
                processedComponents.add(node.name)
            }

            return nodeIncrease
        }
        
        // Start analysis from root nodes
        hierarchy.rootIds
            .filter { !hierarchy.nodes[it]!!.isRevisit }
            .forEach { rootId ->
                analyzeNode(rootId, emptyList())
            }
        
        return suspects
            .sortedByDescending { it["memoryIncrease"] as Int }
            .filter { it["memoryIncrease"] as Int > SIGNIFICANT_INCREASE_MB }
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