package com.korofin.backend.repository.integration;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.entity.integration.TelegramLink;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TelegramLinkRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private TelegramLinkRepository telegramLinkRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByTelegramChatIdReturnsTheLinkedUser() {
        User owner = userRepository.saveAndFlush(newUser("telegram-owner@korofin.dev"));
        telegramLinkRepository.saveAndFlush(newLink(owner, 111L));

        var found = telegramLinkRepository.findByTelegramChatId(111L);

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    void findByTelegramChatIdReturnsEmptyForAnUnlinkedChat() {
        assertThat(telegramLinkRepository.findByTelegramChatId(999L)).isEmpty();
    }

    @Test
    void findByUser_IdReturnsTheUsersLink() {
        User owner = userRepository.saveAndFlush(newUser("telegram-owner2@korofin.dev"));
        telegramLinkRepository.saveAndFlush(newLink(owner, 222L));

        var found = telegramLinkRepository.findByUser_Id(owner.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTelegramChatId()).isEqualTo(222L);
    }

    @Test
    void telegramChatIdIsUniqueAcrossUsers() {
        User first = userRepository.saveAndFlush(newUser("telegram-first@korofin.dev"));
        User second = userRepository.saveAndFlush(newUser("telegram-second@korofin.dev"));
        telegramLinkRepository.saveAndFlush(newLink(first, 333L));

        assertThatThrownBy(() -> telegramLinkRepository.saveAndFlush(newLink(second, 333L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TelegramLink newLink(User owner, Long chatId) {
        TelegramLink link = new TelegramLink();
        link.setUser(owner);
        link.setTelegramChatId(chatId);
        return link;
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
