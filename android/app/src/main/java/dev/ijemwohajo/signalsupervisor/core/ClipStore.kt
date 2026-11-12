package dev.ijemwohajo.signalsupervisor.core

data class AddResult(val saved: Boolean, val evicted: Clip?)

/** 개수 상한 FIFO. 핀 고정된 클립은 밀려나지 않는다. */
class ClipStore(private var limit: Int) {
    private val list = mutableListOf<Clip>()
    val clips: List<Clip> get() = list

    /** 저장된 목록을 그대로 복원한다(상한 초과여도 핀 클립을 지우지 않는다) */
    fun restore(clips: List<Clip>) { list.clear(); list.addAll(clips) }

    fun add(c: Clip): AddResult {
        if (list.size >= limit) {
            val idx = list.indexOfFirst { !it.pinned }
            if (idx < 0) return AddResult(false, null)
            val evicted = list.removeAt(idx)
            list.add(c)
            return AddResult(true, evicted)
        }
        list.add(c)
        return AddResult(true, null)
    }

    fun setPinned(id: String, pinned: Boolean): Boolean {
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(pinned = pinned)
        return i >= 0
    }

    fun get(id: String): Clip? = list.firstOrNull { it.id == id }

    fun setLimit(n: Int) {
        limit = n
        while (list.size > n) {
            val idx = list.indexOfFirst { !it.pinned }
            if (idx < 0) break
            list.removeAt(idx)
        }
    }
}
