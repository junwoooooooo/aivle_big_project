package com.aivle.backend.document.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.validation.UploadedFilePolicy;
import com.aivle.backend.file.validation.ValidatedUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentCommandServiceTests {
    @Mock UploadedFilePolicy policy;
    @Mock IdempotencyKeyPolicy keyPolicy;
    @Mock DocumentRequestFingerprint fingerprint;
    @Mock DocumentUploadTransactionService transactionService;
    @Mock ServicePolicyService servicePolicyService;

    private DocumentCommandService service;
    private final ValidatedUpload validated = new ValidatedUpload(
        new byte[] {0x50, 0x4b, 0x03, 0x04},
        "plan.docx",
        "docx",
        "application/docx",
        "checksum"
    );
    private final DocumentUploadCommand command = new DocumentUploadCommand(
        1L, 2L, DocumentType.BUSINESS_PLAN, "plan.docx", "application/docx",
        4, () -> new ByteArrayInputStream(new byte[] {1}), "key"
    );

    @BeforeEach
    void setUp() {
        service = new DocumentCommandService(
            policy,
            keyPolicy,
            fingerprint,
            transactionService,
            servicePolicyService
        );
        doNothing().when(servicePolicyService).requireWriteAvailableForUser(anyLong());
        doNothing().when(servicePolicyService).requireDocumentProcessingEnabled();
    }

    @Test
    void returnsExistingIdempotentResultWithoutWritingStorage() throws Exception {
        prepareValidation();
        DocumentUploadResult existing = new DocumentUploadResult(
            1L, 3L, 4L, 5L, JobStatus.QUEUED, false
        );
        when(transactionService.findExisting(1L, "key", "fingerprint"))
            .thenReturn(Optional.of(existing));

        assertThat(service.upload(command)).isSameAs(existing);
        verify(transactionService, never()).create(
            any(), any(), any(), any()
        );
    }

    @Test
    void propagatesDatabaseTransactionFailure() throws Exception {
        prepareValidation();
        BusinessException failure = new BusinessException(ErrorCode.VERSION_CONFLICT);
        when(transactionService.create(
            eq(command), eq(validated), eq("key"), eq("fingerprint")
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.upload(command)).isSameAs(failure);
    }

    @Test
    void duplicateCreatedByRaceIsReturned() throws Exception {
        prepareValidation();
        when(transactionService.create(
            eq(command), eq(validated), eq("key"), eq("fingerprint")
        )).thenReturn(new DocumentUploadResult(
            1L, 3L, 4L, 5L, JobStatus.QUEUED, false
        ));

        DocumentUploadResult result = service.upload(command);

        assertThat(result.created()).isFalse();
    }

    private void prepareValidation() throws Exception {
        doNothing().when(transactionService)
            .authorizeUpload(1L, 2L, DocumentType.BUSINESS_PLAN);
        when(keyPolicy.normalize("key")).thenReturn("key");
        when(policy.validate(any(), any())).thenReturn(validated);
        when(fingerprint.calculate(1L, DocumentType.BUSINESS_PLAN, validated))
            .thenReturn("fingerprint");
        when(transactionService.findExisting(1L, "key", "fingerprint"))
            .thenReturn(Optional.empty());
    }

}
