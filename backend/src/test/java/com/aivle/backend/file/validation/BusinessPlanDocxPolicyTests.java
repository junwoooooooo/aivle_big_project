package com.aivle.backend.file.validation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BusinessPlanDocxPolicyTests {
    private static final byte[] DOCX_LIKE = {0x50, 0x4b, 0x03, 0x04, 1, 2, 3};
    private final BusinessPlanDocxPolicy policy = policy(DataSize.ofMegabytes(20));

    @Test
    void acceptsDocxUsingActualBytesAndCalculatesChecksum() throws Exception {
        ValidatedUpload result = policy.validate(
            metadata(" plan.DOCX ", BusinessPlanDocxPolicy.DOCX_MIME, 999),
            new ByteArrayInputStream(DOCX_LIKE)
        );

        assertThat(result.originalFilename()).isEqualTo("plan.DOCX");
        assertThat(result.sizeBytes()).isEqualTo(DOCX_LIKE.length);
        assertThat(result.checksumSha256()).isEqualTo(
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(DOCX_LIKE))
        );
    }

    @Test
    void rejectsNullStream() {
        assertCode(
            () -> policy.validate(metadata("plan.docx", BusinessPlanDocxPolicy.DOCX_MIME, 0), null),
            ErrorCode.FILE_REQUIRED
        );
    }

    @Test
    void rejectsEmptyFile() {
        assertCode(
            () -> policy.validate(
                metadata("plan.docx", BusinessPlanDocxPolicy.DOCX_MIME, 0),
                new ByteArrayInputStream(new byte[0])
            ),
            ErrorCode.FILE_EMPTY
        );
    }

    @Test
    void rejectsActualBytesOverLimitEvenWhenDeclaredSizeIsSmall() {
        BusinessPlanDocxPolicy tiny = policy(DataSize.ofBytes(6));
        assertCode(
            () -> tiny.validate(
                metadata("plan.docx", BusinessPlanDocxPolicy.DOCX_MIME, 1),
                new ByteArrayInputStream(DOCX_LIKE)
            ),
            ErrorCode.FILE_TOO_LARGE
        );
    }

    @Test
    void rejectsPdfExtension() {
        assertCode(
            () -> policy.validate(
                metadata("plan.pdf", BusinessPlanDocxPolicy.DOCX_MIME, DOCX_LIKE.length),
                new ByteArrayInputStream(DOCX_LIKE)
            ),
            ErrorCode.FILE_TYPE_UNSUPPORTED
        );
    }

    @Test
    void rejectsGenericMime() {
        assertCode(
            () -> policy.validate(
                metadata("plan.docx", "application/octet-stream", DOCX_LIKE.length),
                new ByteArrayInputStream(DOCX_LIKE)
            ),
            ErrorCode.FILE_TYPE_UNSUPPORTED
        );
    }

    @Test
    void rejectsFakeDocxSignature() {
        assertCode(
            () -> policy.validate(
                metadata("plan.docx", BusinessPlanDocxPolicy.DOCX_MIME, 4),
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4})
            ),
            ErrorCode.FILE_SIGNATURE_INVALID
        );
    }

    @Test
    void rejectsTraversalFilename() {
        assertCode(
            () -> policy.validate(
                metadata("../plan.docx", BusinessPlanDocxPolicy.DOCX_MIME, DOCX_LIKE.length),
                new ByteArrayInputStream(DOCX_LIKE)
            ),
            ErrorCode.FILE_NAME_INVALID
        );
    }

    @Test
    void rejectsControlCharacterFilename() {
        assertCode(
            () -> policy.validate(
                metadata("plan\u0000.docx", BusinessPlanDocxPolicy.DOCX_MIME, DOCX_LIKE.length),
                new ByteArrayInputStream(DOCX_LIKE)
            ),
            ErrorCode.FILE_NAME_INVALID
        );
    }

    @Test
    void rejectsFilenameWithoutStem() {
        assertCode(
            () -> policy.validate(
                metadata(".docx", BusinessPlanDocxPolicy.DOCX_MIME, DOCX_LIKE.length),
                new ByteArrayInputStream(DOCX_LIKE)
            ),
            ErrorCode.FILE_TYPE_UNSUPPORTED
        );
    }

    private UploadedFileMetadata metadata(String name, String type, long size) {
        return new UploadedFileMetadata(name, type, size);
    }

    private BusinessPlanDocxPolicy policy(DataSize maxSize) {
        return new BusinessPlanDocxPolicy(new FileStorageProperties(
            Path.of("unused"),
            maxSize,
            DataSize.ofMegabytes(10),
            List.of("docx"),
            List.of("png")
        ));
    }

    private void assertCode(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable::call)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call() throws Exception;
    }
}
