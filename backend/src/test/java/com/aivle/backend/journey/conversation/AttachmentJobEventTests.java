package com.aivle.backend.journey.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.jobevent.JobEventPublisher;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttachmentJobEventTests {
    @Test
    void parsingStartPublishesSafeDurableEvent() {
        IdeaAttachmentRepository repository = mock(IdeaAttachmentRepository.class);
        JobEventPublisher publisher = mock(JobEventPublisher.class);
        IdeaAttachment attachment = mock(IdeaAttachment.class);
        StoredFile file = mock(StoredFile.class);
        when(repository.findByIdAndProjectIdAndConversationIdAndDeletedAtIsNull(3L, 1L, 2L))
            .thenReturn(Optional.of(attachment));
        when(attachment.getStoredFile()).thenReturn(file);
        when(attachment.getStatus()).thenReturn(IdeaAttachment.Status.UPLOADED);
        when(file.getStorageKey()).thenReturn("key");
        when(file.getOriginalFilename()).thenReturn("idea.txt");
        when(file.getExtension()).thenReturn("txt");
        when(file.getMimeType()).thenReturn("text/plain");
        when(file.getSizeBytes()).thenReturn(5L);
        IdeaAttachmentStateService service = new IdeaAttachmentStateService(repository, publisher);

        service.start(1L, 2L, 3L, "idea-attachment-3");

        var command = org.mockito.ArgumentCaptor.forClass(JobEventPublisher.Command.class);
        verify(publisher).publish(command.capture());
        assertThat(command.getValue().messageKey()).isEqualTo("job.idea.attachment.parsing.started");
        assertThat(command.getValue().messageParams()).isEmpty();
        verify(attachment).startProcessing();
    }
}
