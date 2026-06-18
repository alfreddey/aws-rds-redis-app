package com.amalitech.todo.web.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper holding the list of tasks for the {@code tasks} cache.
 *
 * <p>Why wrap instead of caching {@code List<TaskResponse>} directly:
 * {@code GenericJackson2JsonRedisSerializer} writes a root-level collection as a plain
 * JSON array with no type wrapper, but on read it treats a root array as a
 * {@code [typeId, payload]} tuple — so it misreads the first element as a type id and
 * fails. Wrapping the list in a POJO makes the cached root a single object carrying
 * {@code @class}, which round-trips reliably; the inner list deserialises from the
 * field's declared type. The controller still returns the bare list to clients.
 */
public class TaskListResponse implements Serializable {

    private List<TaskResponse> tasks = new ArrayList<>();

    public TaskListResponse() {
    }

    public TaskListResponse(List<TaskResponse> tasks) {
        this.tasks = tasks;
    }

    public List<TaskResponse> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskResponse> tasks) {
        this.tasks = tasks;
    }
}
