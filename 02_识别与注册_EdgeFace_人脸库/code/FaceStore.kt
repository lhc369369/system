package com.example.osai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 本地人脸特征库,保存在应用私有目录 faces.json。 */
class FaceStore(context: Context) {

    private val file = File(context.filesDir, "faces.json")
    private val entries = LinkedHashMap<String, FloatArray>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        entries.clear()
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val names = root.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = root.getJSONArray(name)
                val vec = FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
                if (vec.size == 512) entries[name] = vec
            }
        }
    }

    @Synchronized
    private fun persist() {
        val root = JSONObject()
        for ((name, vec) in entries) {
            val arr = JSONArray()
            for (v in vec) arr.put(v.toDouble())
            root.put(name, arr)
        }
        file.writeText(root.toString())
    }

    @Synchronized
    fun labels(): Array<String> = entries.keys.toTypedArray()

    @Synchronized
    fun size(): Int = entries.size

    /** 同名注册视为覆盖,返回 true 表示覆盖了旧模板。 */
    @Synchronized
    fun save(label: String, embedding: FloatArray): Boolean {
        val replaced = entries.containsKey(label)
        entries[label] = embedding.copyOf()
        persist()
        return replaced
    }

    @Synchronized
    fun delete(label: String): Boolean {
        val removed = entries.remove(label) != null
        if (removed) persist()
        return removed
    }

    /** 返回最相似的一条(不论是否超过阈值),由调用方判断阈值。 */
    @Synchronized
    fun best(embedding: FloatArray): Pair<String, Float>? {
        var bestName: String? = null
        var best = -1f
        for ((name, vec) in entries) {
            if (vec.size != embedding.size) continue
            var dot = 0f
            for (i in vec.indices) dot += vec[i] * embedding[i]
            if (dot > best) {
                best = dot
                bestName = name
            }
        }
        return if (bestName != null) bestName to best else null
    }
}
