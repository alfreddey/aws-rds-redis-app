package com.amalitech.todo.config;

import com.amalitech.todo.web.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the cache contract: a {@code List<TaskResponse>} must survive a round trip
 * through the exact serializer the Redis cache uses. This catches the classic
 * "immutable list / missing type info" deserialization failure without needing a
 * live Redis.
 */
class CacheSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Test
    void taskListRoundTripsThroughRedisSerializer() {
        List<TaskResponse> original = new ArrayList<>();
        original.add(sample(1L, "first", false));
        original.add(sample(2L, "second", true));

        byte[] bytes = serializer.serialize(original);
        Object back = serializer.deserialize(bytes);

        assertThat(back).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<TaskResponse> restored = (List<TaskResponse>) back;
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).getTitle()).isEqualTo("first");
        assertThat(restored.get(0).isCompleted()).isFalse();
        assertThat(restored.get(1).getId()).isEqualTo(2L);
        assertThat(restored.get(1).isCompleted()).isTrue();
        assertThat(restored.get(1).getCreatedAt()).isEqualTo("2026-01-01T00:00Z");
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
