package com.korofin.backend.ai.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.ai.entity.AiMessage;
import com.korofin.backend.ai.entity.AiMessageKind;
import com.korofin.backend.ai.entity.AiMessageRole;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AiMessageRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUser_IdAndKindOrderByCreatedAtDescExcludesOtherKindAndOtherUsers() {
        User owner = userRepository.saveAndFlush(newUser("chat-owner@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("chat-other@korofin.dev"));
        aiMessageRepository.saveAndFlush(newMessage(owner, AiMessageRole.USER, AiMessageKind.CHAT, "hola"));
        aiMessageRepository.saveAndFlush(newMessage(owner, AiMessageRole.ASSISTANT, AiMessageKind.INSIGHT, "- ahorra más"));
        aiMessageRepository.saveAndFlush(newMessage(other, AiMessageRole.USER, AiMessageKind.CHAT, "otro usuario"));

        Page<AiMessage> page = aiMessageRepository.findByUser_IdAndKindOrderByCreatedAtDesc(
                owner.getId(), AiMessageKind.CHAT, PageRequest.of(0, 10)
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getContent()).isEqualTo("hola");
    }

    @Test
    void findFirstByUser_IdAndKindOrderByCreatedAtDescReturnsTheMostRecentInsight() throws InterruptedException {
        User owner = userRepository.saveAndFlush(newUser("insight-owner@korofin.dev"));
        aiMessageRepository.saveAndFlush(newMessage(owner, AiMessageRole.ASSISTANT, AiMessageKind.INSIGHT, "- primer insight"));
        Thread.sleep(5);
        aiMessageRepository.saveAndFlush(newMessage(owner, AiMessageRole.ASSISTANT, AiMessageKind.INSIGHT, "- segundo insight"));

        Optional<AiMessage> latest = aiMessageRepository.findFirstByUser_IdAndKindOrderByCreatedAtDesc(owner.getId(), AiMessageKind.INSIGHT);

        assertThat(latest).isPresent();
        assertThat(latest.get().getContent()).isEqualTo("- segundo insight");
    }

    @Test
    void deleteByUserIdAndKindOnlyDeletesChatRowsAndOnlyForThatUser() {
        User owner = userRepository.saveAndFlush(newUser("delete-owner@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("delete-other@korofin.dev"));
        aiMessageRepository.saveAndFlush(newMessage(owner, AiMessageRole.USER, AiMessageKind.CHAT, "borrar"));
        aiMessageRepository.saveAndFlush(newMessage(owner, AiMessageRole.ASSISTANT, AiMessageKind.INSIGHT, "conservar"));
        aiMessageRepository.saveAndFlush(newMessage(other, AiMessageRole.USER, AiMessageKind.CHAT, "no tocar"));

        int deleted = aiMessageRepository.deleteByUserIdAndKind(owner.getId(), AiMessageKind.CHAT);

        assertThat(deleted).isEqualTo(1);
        List<AiMessage> remainingForOwner = aiMessageRepository
                .findByUser_IdAndKindOrderByCreatedAtDesc(owner.getId(), AiMessageKind.INSIGHT, PageRequest.of(0, 10))
                .getContent();
        assertThat(remainingForOwner).hasSize(1);
        List<AiMessage> otherUserChat = aiMessageRepository
                .findByUser_IdAndKindOrderByCreatedAtDesc(other.getId(), AiMessageKind.CHAT, PageRequest.of(0, 10))
                .getContent();
        assertThat(otherUserChat).hasSize(1);
    }

    private AiMessage newMessage(User owner, AiMessageRole role, AiMessageKind kind, String content) {
        AiMessage message = new AiMessage();
        message.setUser(owner);
        message.setRole(role);
        message.setKind(kind);
        message.setContent(content);
        return message;
    }

    private User newUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }
}
