package com.amalitech.todo.service;

import com.amalitech.todo.domain.Task;
import com.amalitech.todo.repository.TaskRepository;
import com.amalitech.todo.web.ResourceNotFoundException;
import com.amalitech.todo.web.dto.TaskRequest;
import com.amalitech.todo.web.dto.TaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for tasks, demonstrating the read-through cache pattern:
 *
 * <ul>
 *   <li>Reads ({@link #list()}, {@link #get(Long)}) are {@code @Cacheable} — served
 *       from ElastiCache Redis when warm; on a miss the method runs, hits PostgreSQL
 *       through RDS Proxy, and the result is cached.</li>
 *   <li>Writes ({@link #create}, {@link #update}, {@link #delete}) persist to RDS via
 *       the proxy and {@code @CacheEvict} the cached views so the next read repopulates
 *       the cache from the source of truth.</li>
 * </ul>
 *
 * The cache-miss branches log explicitly, so CloudWatch Logs show exactly when a
 * request reached the database versus being served from Redis.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    static final String TASKS_CACHE = "tasks";
    static final String TASK_CACHE = "task";

    private final TaskRepository repository;
    private final long simulatedReadDelayMs;

    public TaskService(TaskRepository repository,
                       @Value("${app.simulated-read-delay-ms:0}") long simulatedReadDelayMs) {
        this.repository = repository;
        this.simulatedReadDelayMs = simulatedReadDelayMs;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = TASKS_CACHE, key = "'all'")
    public List<TaskResponse> list() {
        log.info("CACHE MISS [tasks:all] - reading all tasks from PostgreSQL via RDS Proxy");
        simulateReadLatency();
        // Collect into a concrete ArrayList: GenericJackson2JsonRedisSerializer records the
        // runtime type, and an immutable list (Stream.toList()) cannot be reconstructed on a
        // cache hit. ArrayList round-trips cleanly.
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(TaskResponse::from)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = TASK_CACHE, key = "#id")
    public TaskResponse get(Long id) {
        log.info("CACHE MISS [task:{}] - reading task from PostgreSQL via RDS Proxy", id);
        simulateReadLatency();
        return repository.findById(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + id + " not found"));
    }

    @Transactional
    @CacheEvict(cacheNames = {TASKS_CACHE, TASK_CACHE}, allEntries = true)
    public TaskResponse create(TaskRequest request) {
        Task saved = repository.save(new Task(request.title(), request.description(), request.completed()));
        log.info("Created task {} (cache evicted)", saved.getId());
        return TaskResponse.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {TASKS_CACHE, TASK_CACHE}, allEntries = true)
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + id + " not found"));
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCompleted(request.completed());
        Task saved = repository.save(task);
        log.info("Updated task {} (cache evicted)", id);
        return TaskResponse.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {TASKS_CACHE, TASK_CACHE}, allEntries = true)
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Task " + id + " not found");
        }
        repository.deleteById(id);
        log.info("Deleted task {} (cache evicted)", id);
    }

    /**
     * Optional demo aid: when {@code SIMULATED_READ_DELAY_MS} > 0, the database-backed
     * read path sleeps for that long, making the speed-up of a Redis cache hit obvious
     * in the UI and in latency metrics. Defaults to 0 (no delay).
     */
    private void simulateReadLatency() {
        if (simulatedReadDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(simulatedReadDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
