package com.aivle.backend.document.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.validation.UploadedFileMetadata;
import com.aivle.backend.file.validation.UploadedFilePolicy;
import com.aivle.backend.file.validation.ValidatedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentCommandService {
    private final UploadedFilePolicy uploadedFilePolicy;
    private final IdempotencyKeyPolicy idempotencyKeyPolicy;
    private final DocumentRequestFingerprint fingerprintCalculator;
    private final DocumentUploadTransactionService transactionService;
    private final ServicePolicyService servicePolicy;

    public DocumentUploadResult upload(DocumentUploadCommand command) {
        servicePolicy.requireWriteAvailableForUser(command.userId());
        servicePolicy.requireDocumentProcessingEnabled();
        transactionService.authorizeUpload(
            command.projectId(),
            command.userId(),
            command.documentType()
        );
        String idempotencyKey = idempotencyKeyPolicy.normalize(command.idempotencyKey());
        ValidatedUpload upload = validate(command);
        String fingerprint = fingerprintCalculator.calculate(
            command.projectId(),
            command.documentType(),
            upload
        );

        Optional<DocumentUploadResult> existing = transactionService.findExisting(
            command.projectId(),
            idempotencyKey,
            fingerprint
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        return transactionService.create(
            command,
            upload,
            idempotencyKey,
            fingerprint
        );
    }

    private ValidatedUpload validate(DocumentUploadCommand command) {
        if (command.content() == null) {
            throw new BusinessException(ErrorCode.FILE_REQUIRED);
        }
        try (InputStream input = command.content().openStream()) {
            return uploadedFilePolicy.validate(
                new UploadedFileMetadata(
                    command.originalFilename(),
                    command.contentType(),
                    command.declaredSize()
                ),
                input
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

}
