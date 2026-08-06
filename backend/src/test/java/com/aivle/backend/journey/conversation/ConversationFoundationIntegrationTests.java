package com.aivle.backend.journey.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversationFoundationIntegrationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired IdeaAttachmentRepository attachments;
    @Autowired StoredFileRepository storedFiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;

    @Test
    void createsCurrentConversationAndPersistsOrderedMessages() {
        User owner = users.saveAndFlush(User.create("conversation-owner@example.com", "hashed", "conversation-owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "conversation", null, "AI"));

        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        conversations.appendMessage(owner.getId(), project.getId(), conversation.getId(), IdeaMessage.Role.USER, "첫 메시지");
        IdeaMessage assistant = conversations.appendMessage(owner.getId(), project.getId(), conversation.getId(), IdeaMessage.Role.ASSISTANT, "확인 질문");

        assertThat(conversations.current(owner.getId(), project.getId()).getId()).isEqualTo(conversation.getId());
        assertThat(conversations.messages(owner.getId(), project.getId(), conversation.getId()))
            .extracting(IdeaMessage::getSequenceNumber).containsExactly(1, 2);
        assertThat(assistant.getSchemaVersion()).isEqualTo("1.0");
        assertThat(IdeaMessageContract.view(mapper, assistant).envelope().messageType())
            .isEqualTo(IdeaMessageContract.Type.TEXT);

        conversations.close(owner.getId(), project.getId(), conversation.getId());
        assertThat(conversation.getStatus()).isEqualTo(IdeaConversation.Status.CLOSED);
        assertThatThrownBy(() -> conversations.appendMessage(owner.getId(), project.getId(), conversation.getId(),
            IdeaMessage.Role.USER, "닫힌 대화"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corruptedAssistantEnvelopeIsRejectedInsteadOfDowngradedToText() {
        User owner = users.saveAndFlush(User.create("envelope-owner@example.com", "hashed", "envelope-owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "envelope", null, null));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        IdeaMessage assistant = conversations.appendMessage(owner.getId(), project.getId(), conversation.getId(),
            IdeaMessage.Role.ASSISTANT, "확인 질문");
        jdbc.update("update idea_messages set payload_json=? where id=?", "{broken", assistant.getId());
        entityManager.clear();
        assistant = conversations.messages(owner.getId(), project.getId(), conversation.getId()).get(0);
        IdeaMessage corrupted = assistant;
        assertThatThrownBy(() -> IdeaMessageContract.view(mapper, corrupted))
            .isInstanceOf(IdeaMessageContract.InvalidEnvelopeException.class)
            .hasMessage("구조화 메시지를 표시할 수 없습니다.");
    }

    @Test
    void rejectsAccessFromAnotherProjectOwner() {
        User owner = users.saveAndFlush(User.create("conversation-owner-2@example.com", "hashed", "conversation-owner-2"));
        User outsider = users.saveAndFlush(User.create("conversation-outsider@example.com", "hashed", "conversation-outsider"));
        Project project = projects.saveAndFlush(Project.create(owner, "private conversation", null, null));
        conversations.create(owner.getId(), project.getId(), null);

        assertThatThrownBy(() -> conversations.current(outsider.getId(), project.getId()))
            .isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    @Test
    void attachmentTransitionsDoNotInventAnExtractedValue() {
        User owner = users.saveAndFlush(User.create("attachment-owner@example.com", "hashed", "attachment-owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "attachment", null, null));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        IdeaMessage message = conversations.appendMessage(owner.getId(), project.getId(), conversation.getId(),
            IdeaMessage.Role.USER, "문서를 첨부합니다");
        StoredFile storedFile = storedFiles.saveAndFlush(StoredFile.available("conversation/file-1", "idea.docx",
            "stored.docx", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            128, "a".repeat(64)));
        IdeaAttachment attachment = attachments.save(IdeaAttachment.uploaded(conversation, message, storedFile));

        attachment.startProcessing();
        assertThatThrownBy(() -> attachment.extracted("not-a-hash"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(attachment.getStatus()).isEqualTo(IdeaAttachment.Status.PROCESSING);
        attachment.extracted("sha256:" + "b".repeat(64));
        assertThat(attachment.getStatus()).isEqualTo(IdeaAttachment.Status.EXTRACTED);
    }
}
