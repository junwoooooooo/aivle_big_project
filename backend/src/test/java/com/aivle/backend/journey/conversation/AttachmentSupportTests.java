package com.aivle.backend.journey.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AttachmentSupportTests {
    @Test
    void onlyTxtAndDocxWithMatchingDeclaredTypesAreSupported() {
        assertThat(IdeaAttachmentService.supports("txt", "text/plain;charset=UTF-8")).isTrue();
        assertThat(IdeaAttachmentService.supports("docx", IdeaAttachmentService.DOCX_MIME)).isTrue();
        assertThat(IdeaAttachmentService.supports("pdf", "application/pdf")).isFalse();
        assertThat(IdeaAttachmentService.supports("csv", "text/csv")).isFalse();
        assertThat(IdeaAttachmentService.supports("docx", "image/png")).isFalse();
    }
}
