package com.amalitech.todo.service;

import com.amalitech.todo.domain.Task;
import com.amalitech.todo.repository.TaskRepository;
import com.amalitech.todo.web.ResourceNotFoundException;
import com.amalitech.todo.web.dto.TaskRequest;
import com.amalitech.todo.web.dto.TaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests (Mockito, no Spring context) so {@code mvn verify} is green without
 * a live database or Redis. The cache/JPA wiring is exercised against the real backends
 * once deployed.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository, 0L);
    }

    @Test
    void listMapsEntitiesToResponsesNewestFirst() {
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(new Task("b", "second", false), new Task("a", "first", true)));

        List<TaskResponse> result = service.listTasks().getTasks();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("b");
        assertThat(result.get(1).isCompleted()).isTrue();
    }

    @Test
    void createPersistsAndReturnsResponse() {
        when(repository.saveAndFlush(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse created = service.create(new TaskRequest("write report", "due friday", false));

        assertThat(created.getTitle()).isEqualTo("write report");
        assertThat(created.getDescription()).isEqualTo("due friday");
        verify(repository).saveAndFlush(any(Task.class));
    }

    @Test
    void getMissingTaskThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteMissingTaskThrowsNotFound() {
        when(repository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
