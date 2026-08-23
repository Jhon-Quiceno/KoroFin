package com.korofin.backend.service.integration.telegram;

import com.korofin.backend.dto.integration.TelegramLinkCodeResponse;
import com.korofin.backend.entity.integration.TelegramLink;
import com.korofin.backend.entity.user.User;
import com.korofin.backend.exception.integration.TelegramChatNotLinkedException;
import com.korofin.backend.exception.integration.TelegramInvalidLinkCodeException;
import com.korofin.backend.repository.integration.TelegramLinkRepository;
import com.korofin.backend.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramLinkServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CHAT_ID = 555L;

    @Mock
    private TelegramLinkRepository telegramLinkRepository;

    @Mock
    private TelegramLinkCodeStore telegramLinkCodeStore;

    @Mock
    private UserRepository userRepository;

    private TelegramLinkService telegramLinkService;

    @BeforeEach
    void setUp() {
        telegramLinkService = new TelegramLinkService(telegramLinkRepository, telegramLinkCodeStore, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateLinkCodeDelegatesToTheCodeStoreForTheCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null));
        when(telegramLinkCodeStore.generate(USER_ID)).thenReturn("123456");
        when(telegramLinkCodeStore.ttlSeconds()).thenReturn(300L);

        TelegramLinkCodeResponse response = telegramLinkService.generateLinkCode();

        assertThat(response.code()).isEqualTo("123456");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void confirmLinkCreatesANewLinkWhenTheChatWasNeverLinked() {
        when(telegramLinkCodeStore.consume("123456")).thenReturn(Optional.of(USER_ID));
        when(telegramLinkRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.empty());
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        telegramLinkService.confirmLink("123456", CHAT_ID);

        ArgumentCaptor<TelegramLink> captor = ArgumentCaptor.forClass(TelegramLink.class);
        verify(telegramLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getTelegramChatId()).isEqualTo(CHAT_ID);
        assertThat(captor.getValue().getUser().getId()).isEqualTo(USER_ID);
    }

    @Test
    void confirmLinkReassignsAnExistingLinkToTheNewUser() {
        when(telegramLinkCodeStore.consume("654321")).thenReturn(Optional.of(2L));
        TelegramLink existingLink = new TelegramLink();
        existingLink.setId(9L);
        existingLink.setTelegramChatId(CHAT_ID);
        when(telegramLinkRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.of(existingLink));
        User newOwner = new User();
        newOwner.setId(2L);
        when(userRepository.getReferenceById(2L)).thenReturn(newOwner);

        telegramLinkService.confirmLink("654321", CHAT_ID);

        ArgumentCaptor<TelegramLink> captor = ArgumentCaptor.forClass(TelegramLink.class);
        verify(telegramLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getUser().getId()).isEqualTo(2L);
    }

    @Test
    void confirmLinkThrowsWhenTheCodeIsInvalidOrExpired() {
        when(telegramLinkCodeStore.consume("bad-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telegramLinkService.confirmLink("bad-code", CHAT_ID))
                .isInstanceOf(TelegramInvalidLinkCodeException.class);
    }

    @Test
    void resolveUserIdReturnsTheLinkedUser() {
        TelegramLink link = new TelegramLink();
        User user = new User();
        user.setId(USER_ID);
        link.setUser(user);
        when(telegramLinkRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.of(link));

        Long resolved = telegramLinkService.resolveUserId(CHAT_ID);

        assertThat(resolved).isEqualTo(USER_ID);
    }

    @Test
    void resolveUserIdThrowsWhenTheChatIsNotLinked() {
        when(telegramLinkRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telegramLinkService.resolveUserId(CHAT_ID))
                .isInstanceOf(TelegramChatNotLinkedException.class);
    }
}
