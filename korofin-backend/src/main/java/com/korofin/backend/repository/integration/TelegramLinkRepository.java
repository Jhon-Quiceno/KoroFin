package com.korofin.backend.repository.integration;

import com.korofin.backend.entity.integration.TelegramLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a persistencia de {@link TelegramLink}.
 */
public interface TelegramLinkRepository extends JpaRepository<TelegramLink, Long> {

    Optional<TelegramLink> findByTelegramChatId(Long telegramChatId);

    Optional<TelegramLink> findByUser_Id(Long userId);
}
