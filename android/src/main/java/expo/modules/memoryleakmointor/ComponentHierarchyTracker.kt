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
    var memorySnapshot: ComponentMemorySnapshot? = null
)

data class ComponentHierarchy(
    val nodes: MutableMap<String, ComponentNode> = mutableMapOf(),
    val rootIds: MutableSet<String> = mutableSetOf()
)

class ComponentHierarchyTracker(private val context: Context) {
    private val hierarchy = ComponentHierarchy()
    private val parentStack = mutableListOf<String>()
    
    fun getNode(componentId: String): ComponentNode? {
        return hierarchy.nodes[componentId]
    }
    
    fun trackComponent(componentName: String): String {
        val componentId = generateComponentId(componentName)
        val parentId = parentStack.lastOrNull()
        
        val node = ComponentNode(
            id = componentId,
            name = componentName,
            parentId = parentId,
            mountTime = System.currentTimeMillis()
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
    
    fun untrackComponent(componentId: String) {
        val node = hierarchy.nodes[componentId] ?: return
        node.unmountTime = System.currentTimeMillis()
        parentStack.remove(componentId)
    }
    
    fun generateHierarchyReport(): Map<String, Any> {
        val treeStructure = hierarchy.rootIds.map { rootId ->
            buildTreeNode(rootId)
        }

        val metrics = calculateHierarchyMetrics()
        val leakingSuspects = findLeakingSuspects()
        
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
    
    private fun buildTreeNode(nodeId: String): Map<String, Any> {
        val node = hierarchy.nodes[nodeId] ?: return emptyMap()
        
        val nodeData = mutableMapOf<String, Any>(
            "id" to node.id,
            "name" to node.name,
            "mountTime" to node.mountTime
        )
        
        node.unmountTime?.let { nodeData["unmountTime"] = it }
        node.memorySnapshot?.let { snapshot ->
            nodeData["memoryData"] = mapOf(
                "baseline" to snapshot.baselineMemory,
                "peak" to snapshot.peakMemory,
                "final" to snapshot.previousMemory,
                "totalChange" to (snapshot.previousMemory - snapshot.baselineMemory),
                "readings" to snapshot.readings
            )
        }
        
        if (node.children.isNotEmpty()) {
            nodeData["children"] = node.children.map { childId ->
                buildTreeNode(childId)
            }
        }
        
        return nodeData
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