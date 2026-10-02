package com.example.jobs

import com.example.core.model.PrintJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

class LocalJobQueue {
    private val deque = ConcurrentLinkedDeque<PrintJob>()
    private val _jobsFlow = MutableStateFlow<List<PrintJob>>(emptyList())
    val jobsFlow: StateFlow<List<PrintJob>> = _jobsFlow.asStateFlow()

    fun addJob(job: PrintJob) {
        deque.addFirst(job)
        while (deque.size > 20) {
            deque.pollLast()
        }
        _jobsFlow.value = deque.toList()
    }

    fun updateJob(job: PrintJob) {
        val list = deque.map { if (it.id == job.id) job else it }
        deque.clear()
        deque.addAll(list)
        _jobsFlow.value = deque.toList()
    }

    fun clear() {
        deque.clear()
        _jobsFlow.value = emptyList()
    }
}
