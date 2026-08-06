package com.aivle.backend.journey.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aivle.backend.document.parsing.DocumentParser;
import com.aivle.backend.file.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentParsingTests {
    @Test
    void txtParsingCompletesWithDeterministicHash() throws Exception {
        IdeaAttachmentStateService state = mock(IdeaAttachmentStateService.class);
        FileStorage storage = mock(FileStorage.class);
        when(state.start(1L, 2L, 3L, "idea-attachment-3")).thenReturn(
            new IdeaAttachmentStateService.ProcessingInput("key", "idea.txt", "txt", "text/plain", 5));
        when(storage.open("key")).thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        IdeaAttachmentProcessor processor = new IdeaAttachmentProcessor(state, storage, mock(DocumentParser.class));

        processor.process(1L, 2L, 3L, "idea-attachment-3");

        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(state).complete(eq(1L), eq(2L), eq(3L), eq("idea-attachment-3"), hash.capture());
        assertThat(hash.getValue()).isEqualTo("sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        verify(state, never()).fail(any(), any(), any(), any(), any());
    }

    @Test
    void parsingFailureMovesAttachmentToSafeFailureState() throws Exception {
        IdeaAttachmentStateService state = mock(IdeaAttachmentStateService.class);
        FileStorage storage = mock(FileStorage.class);
        when(state.start(1L, 2L, 3L, "idea-attachment-3")).thenReturn(
            new IdeaAttachmentStateService.ProcessingInput("key", "idea.txt", "txt", "text/plain", 5));
        when(storage.open("key")).thenThrow(new IOException("disk unavailable"));
        IdeaAttachmentProcessor processor = new IdeaAttachmentProcessor(state, storage, mock(DocumentParser.class));

        assertThatThrownBy(() -> processor.process(1L, 2L, 3L, "idea-attachment-3"))
            .isInstanceOf(IdeaAttachmentProcessor.AttachmentProcessingException.class);
        verify(state, never()).complete(any(), any(), any(), any(), any());
    }
}
