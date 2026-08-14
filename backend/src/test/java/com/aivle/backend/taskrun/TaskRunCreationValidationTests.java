package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class TaskRunCreationValidationTests {
    @Test
    void inputLargerThanTwoMebibytesRemainsAValidationFailure() {
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = mock(Project.class);
        User owner = mock(User.class);
        when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
        when(project.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(7L);
        TaskRunService service = new TaskRunService(
            mock(TaskRunRepository.class),
            mock(TaskAttemptRepository.class),
            mock(TaskResultRepository.class),
            projects,
            Optional.<Clock>empty(),
            new ObjectMapper(),
            mock(CanonicalInputHasher.class),
            mock(ServicePolicyService.class)
        );
        String oversized = "{\"payload\":\"" + "x".repeat(2 * 1024 * 1024) + "\"}";

        assertThatThrownBy(() -> service.createWithDisposition(
                7L, 41L, TaskType.MARKET_RESEARCH, "MARKET_RESEARCH_FULL", "concept-1",
                oversized, "sha256:" + "a".repeat(64), "market-key", "request-1", 1))
            .isInstanceOfSatisfying(TaskRunFailure.class, failure -> {
                assertThat(failure.getCode()).isEqualTo("VALIDATION_ERROR");
                assertThat(failure.getReason()).isEqualTo("TASK_RUN_INPUT_INVALID");
                assertThat(failure.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }
}
