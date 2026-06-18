package com.amalitech.todo.web.dto;

import com.amalitech.todo.domain.Task;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Outbound view of a task. Plain POJO with String timestamps so it serialises
 * cleanly to/from Redis (via {@code GenericJackson2JsonRedisSerializer}) without
 * needing a JSR-310 module, and is reconstructed exactly on a cache hit.
 */
public class TaskResponse implements Serializable {

    private Long id;
    private String title;
    private String description;
    private boolean completed;
    private String createdAt;
    private String updatedAt;

    public TaskResponse() {
        // for Jackson deserialisation (cache reads)
    }

    public static TaskResponse from(Task task) {
        TaskResponse r = new TaskResponse();
        r.id = task.getId();
        r.title = task.getTitle();
        r.description = task.getDescription();
        r.completed = task.isCompleted();
        r.createdAt = toIso(task.getCreatedAt());
        r.updatedAt = toIso(task.getUpdatedAt());
        return r;
    }

    private static String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
