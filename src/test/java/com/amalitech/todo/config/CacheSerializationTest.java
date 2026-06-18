package com.amalitech.todo.config;

import com.amalitech.todo.web.dto.TaskListResponse;
import com.amalitech.todo.web.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the cache contract: the value the {@code tasks} cache stores
 * ({@link TaskListResponse}) must survive a round trip through the exact serializer the
 * Redis cache uses. Caching a root-level {@code List} fails with this serializer (the
 * root array is misread as a {@code [typeId, payload]} tuple) — wrapping it in a POJO
 * fixes that, and this test pins the behaviour without needing a live Redis.
 */
class CacheSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Test
    void taskListResponseRoundTripsThroughRedisSerializer() {
        List<TaskResponse> tasks = new ArrayList<>();
        tasks.add(sample(1L, "first", false));
        tasks.add(sample(2L, "second", true));
        TaskListResponse original = new TaskListResponse(tasks);

        byte[] bytes = serializer.serialize(original);
        Object back = serializer.deserialize(bytes);

        assertThat(back).isInstanceOf(TaskListResponse.class);
        TaskListResponse restored = (TaskListResponse) back;
        assertThat(restored.getTasks()).hasSize(2);
        assertThat(restored.getTasks().get(0).getTitle()).isEqualTo("first");
        assertThat(restored.getTasks().get(0).isCompleted()).isFalse();
        assertThat(restored.getTasks().get(1).getId()).isEqualTo(2L);
        assertThat(restored.getTasks().get(1).isCompleted()).isTrue();
        assertThat(restored.getTasks().get(1).getCreatedAt()).isEqualTo("2026-01-01T00:00Z");
    }

    private static TaskResponse sample(long id, String title, boolean completed) {
        TaskResponse r = new TaskResponse();
        r.setId(id);
        r.setTitle(title);
        r.setDescription("notes for " + title);
        r.setCompleted(completed);
        r.setCreatedAt("2026-01-01T00:00Z");
        r.setUpdatedAt("2026-01-02T00:00Z");
        return r;
    }
}
