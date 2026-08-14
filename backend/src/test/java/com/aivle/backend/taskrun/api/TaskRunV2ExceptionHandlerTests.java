package com.aivle.backend.taskrun.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.common.web.RequestIdFilter;
import com.aivle.backend.common.web.RequestIds;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class TaskRunV2ExceptionHandlerTests {
    @Test
    void existingTaskRunErrorEnvelopeAndCorrelationHeaderRemainIntact() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new TaskRunV2ExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();

        mvc.perform(get("/taskrun-handler-contract"))
            .andExpect(status().isConflict())
            .andExpect(header().exists("X-Correlation-Id"))
            .andExpect(header().exists(RequestIds.HEADER))
            .andExpect(jsonPath("$.error.code").value("TASK_ALREADY_RUNNING"))
            .andExpect(jsonPath("$.error.details[0].reason").value("SAME_INPUT_ACTIVE"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/taskrun-handler-contract")
        void fail() {
            throw new TaskRunFailure(
                "TASK_ALREADY_RUNNING", "SAME_INPUT_ACTIVE", HttpStatus.CONFLICT, false);
        }
    }
}
