package com.aivle.backend.document.processing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.entity.FileStatus;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.document.application.processing.DocumentJobContext;
import com.aivle.backend.document.application.processing.ParserArtifactObjectService;
import com.aivle.backend.document.application.processing.ParserArtifactPayload;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.job.runner.JobProcessingException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParserArtifactObjectServiceTests {
    @Mock ObjectStoragePort storage;

    @Test
    void finalUploadFailureDeletesTemporaryObject() throws Exception {
        ParserArtifactObjectService service =
            new ParserArtifactObjectService(
                storage,
                new ObjectKeyGenerator()
            );
        ParserArtifactPayload payload =
            new ParserArtifactPayload(
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "a".repeat(64),
                1,
                1,
                "document-blocks-v1"
            );
        when(storage.exists(any())).thenReturn(false);
        when(storage.store(
            any(),
            anyLong(),
            any(),
            contains("/parser/tmp/")
        )).thenAnswer(invocation ->
            new ObjectStoragePort.StoredObject(
                invocation.getArgument(3),
                2,
                "application/json",
                "a".repeat(64)
            )
        );
        when(storage.metadata(contains("/parser/tmp/")))
            .thenAnswer(invocation ->
                new ObjectStoragePort.ObjectMetadata(
                    invocation.getArgument(0),
                    2,
                    "application/json"
                )
            );
        when(storage.store(
            any(),
            anyLong(),
            any(),
            contains("/parser/spring-docx-blocks-v2/")
        )).thenThrow(new IOException("upload failed"));

        assertThatThrownBy(() -> service.store(
            context(),
            "spring-docx-blocks-v2",
            payload
        )).isInstanceOfSatisfying(
            JobProcessingException.class,
            failure -> org.assertj.core.api.Assertions
                .assertThat(failure.getErrorCode())
                .isEqualTo("PARSER_ARTIFACT_STORAGE_FAILED")
        );

        verify(storage).delete(contains("/parser/tmp/"));
    }

    private DocumentJobContext context() {
        return new DocumentJobContext(
            1L,
            1L,
            2L,
            3L,
            StorageType.S3_COMPATIBLE,
            "source.docx",
            "plan.docx",
            "application/vnd.openxmlformats-officedocument"
                + ".wordprocessingml.document",
            10,
            "b".repeat(64),
            FileStatus.AVAILABLE,
            false
        );
    }
}
