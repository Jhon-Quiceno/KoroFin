package com.korofin.backend.integration.service.telegram;

import com.korofin.backend.integration.dto.TelegramLinkCodeResponse;
import com.korofin.backend.integration.entity.TelegramLink;
import com.korofin.backend.integration.exception.TelegramChatNotLinkedException;
import com.korofin.backend.integration.exception.TelegramInvalidLinkCodeException;
import com.korofin.backend.integration.repository.TelegramLinkRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo de vínculo entre un chat de Telegram y el usuario actual de KoroFin (dos pasos, dos
 * actores distintos — ver docs/backend-plan.md sección 5):
 * <ol>
 *   <li>La app (con JWT) llama {@link #generateLinkCode()} → un código de un solo uso, TTL corto.</li>
 *   <li>El usuario le manda ese código al bot de Telegram; n8n reenvía la confirmación a
 *       {@link #confirmLink} (sin JWT, protegido por {@code TelegramWebhookFilter}) para completar
 *       el vínculo {@code chatId ↔ userId}.</li>
 * </ol>
 */
@Service
public class TelegramLinkService {

    private final TelegramLinkRepository telegramLinkRepository;
    private final TelegramLinkCodeStore telegramLinkCodeStore;
    private final UserRepository userRepository;

    public TelegramLinkService(
            TelegramLinkRepository telegramLinkRepository,
            TelegramLinkCodeStore telegramLinkCodeStore,
            UserRepository userRepository
    ) {
        this.telegramLinkRepository = telegramLinkRepository;
        this.telegramLinkCodeStore = telegramLinkCodeStore;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public TelegramLinkCodeResponse generateLinkCode() {
        Long userId = SecurityUtils.getCurrentUserId();
        String code = telegramLinkCodeStore.generate(userId);
        return new TelegramLinkCodeResponse(code, telegramLinkCodeStore.ttlSeconds());
    }

    /**
     * Completa el vínculo {@code chatId ↔ userId} del usuario dueño de {@code code}. Si
     * {@code telegramChatId} ya estaba vinculado a otro usuario, la fila existente se re-asigna
     * (actualiza {@code user}) en vez de fallar por duplicado — ver Javadoc de
     * {@link TelegramLink}.
     *
     * @throws TelegramInvalidLinkCodeException si {@code code} no existe, ya se consumió, o expiró
     */
    @Transactional
    public void confirmLink(String code, Long telegramChatId) {
        Long userId = telegramLinkCodeStore.consume(code)
                .orElseThrow(() -> new TelegramInvalidLinkCodeException(
                        "El código ingresado no es válido o ya expiró. Generá uno nuevo desde la app."
                ));

        TelegramLink link = telegramLinkRepository.findByTelegramChatId(telegramChatId)
                .orElseGet(() -> {
                    TelegramLink created = new TelegramLink();
                    created.setTelegramChatId(telegramChatId);
                    return created;
                });

        link.setUser(userRepository.getReferenceById(userId));
        telegramLinkRepository.save(link);
    }

    /**
     * @return el {@code userId} vinculado a {@code telegramChatId}
     * @throws TelegramChatNotLinkedException si ese chat todavía no completó el flujo de vínculo
     */
    @Transactional(readOnly = true)
    public Long resolveUserId(Long telegramChatId) {
        return telegramLinkRepository.findByTelegramChatId(telegramChatId)
                .map(link -> link.getUser().getId())
                .orElseThrow(TelegramChatNotLinkedException::new);
    }
}
