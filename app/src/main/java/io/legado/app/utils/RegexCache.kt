package io.legado.app.utils

import java.util.LinkedHashMap

/**
 * 正则表达式全局缓存
 *
 * 避免每次匹配都重新编译正则表达式，大幅减少内存和CPU消耗
 * 使用 LinkedHashMap(accessOrder=true) 实现 LRU 淘汰策略
 * 上限 256 个条目，防止无界增长导致内存泄漏
 */
object RegexCache {

    /** 缓存容量上限 */
    private const val MAX_SIZE = 256

    /** LRU 缓存：accessOrder=true 使访问过的条目移至尾部，头部为最久未使用 */
    private val cache = object : LinkedHashMap<String, Regex>(
        (MAX_SIZE / 0.75f).toInt() + 1,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean {
            return size > MAX_SIZE
        }
    }

    /** 同步锁，保证线程安全 */
    private val lock = Any()

    /**
     * 获取或编译正则表达式
     * 缓存命中直接返回，未命中则编译并缓存（LRU淘汰最久未使用的）
     */
    fun getOrCompile(pattern: String): Regex {
        synchronized(lock) {
            cache[pattern]?.let { return it }
            val compiled = Regex(pattern)
            cache[pattern] = compiled
            return compiled
        }
    }

    /**
     * 获取或编译正则表达式（带选项）
     * 缓存命中直接返回，未命中则编译并缓存
     */
    fun getOrCompile(pattern: String, option: RegexOption): Regex {
        val key = "$option:$pattern"
        synchronized(lock) {
            cache[key]?.let { return it }
            val compiled = Regex(pattern, option)
            cache[key] = compiled
            return compiled
        }
    }

    /**
     * 获取或编译正则表达式（自定义编译逻辑）
     *
     * 当编译方式不是简单的 Regex(pattern) 时（如需要先转义）
     * 使用此重载，以 [key] 作为缓存键，[compiler] 作为编译函数
     */
    fun getOrCompile(key: String, compiler: () -> Regex): Regex {
        synchronized(lock) {
            cache[key]?.let { return it }
            val compiled = compiler()
            cache[key] = compiled
            return compiled
        }
    }

    /**
     * 清除缓存
     * 在规则变更时调用，避免旧规则残留
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    /** 当前缓存大小（调试用） */
    fun size(): Int {
        synchronized(lock) {
            return cache.size
        }
    }
}
