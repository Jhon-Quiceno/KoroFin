package com.korofin.backend.ai.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.ai.entity.AiUsageEvent;
import com.korofin.backend.ai.entity.AiUsageEventType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code createdAt} está gobernado por {@code @CreatedDate}/{@code AuditingEntityListener} (ver
 * {@link AiUsageEvent}): la auditoría de JPA sobreescribe cualquier valor asignado a mano antes de
 * {@code save}, así que estos tests nunca fijan esa columna directamente — usan una ventana
 * {@code [ahora - 60s, ahora + 60s]} alrededor de {@link Instant#now()}, igual que el resto de los
 * tests de este proyecto que dependen de columnas auditadas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AiUsageEventRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private AiUsageEventRepository aiUsageEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aggregateByEventTypeGroupsTokensAndCountsOnlySuccessfulEventsInRange() {
        User owner = userRepository.saveAndFlush(newUser("usage-owner@korofin.dev"));
        aiUsageEventRepository.saveAndFlush(newEvent(owner, AiUsageEventType.CHAT, 100, true));
        aiUsageEventRepository.saveAndFlush(newEvent(owner, AiUsageEventType.CHAT, 50, true));
        // Intento fallido: debe quedar excluido del agregado (ver el Javadoc de AiUsageEventRepository).
        aiUsageEventRepository.saveAndFlush(newEvent(owner, AiUsageEventType.CHAT, 999, false));
        aiUsageEventRepository.saveAndFlush(newEvent(owner, AiUsageEventType.CATEGORIZE, 20, true));

        Instant start = Instant.now().minusSeconds(60);
        Instant end = Instant.now().plusSeconds(60);
        List<AiUsageEventRepository.AiUsageEventTypeAggregate> aggregates =
                aiUsageEventRepository.aggregateByEventType(owner.getId(), start, end);

        AiUsageEventRepository.AiUsageEventTypeAggregate chatAggregate = aggregates.stream()
                .filter(row -> row.getEventType() == AiUsageEventType.CHAT)
                .findFirst().orElseThrow();
        assertThat(chatAggregate.getEventCount()).isEqualTo(2);
        assertThat(chatAggregate.getTotalTokens()).isEqualTo(150);

        AiUsageEventRepository.AiUsageEventTypeAggregate categorizeAggregate = aggregates.stream()
                .filter(row -> row.getEventType() == AiUsageEventType.CATEGORIZE)
                .findFirst().orElseThrow();
        assertThat(categorizeAggregate.getEventCount()).isEqualTo(1);
        assertThat(categorizeAggregate.getTotalTokens()).isEqualTo(20);
    }

    @Test
    void aggregateByEventTypeExcludesEventsOutsideTheRequestedWindow() {
        User owner = userRepository.saveAndFlush(newUser("usage-outside-window@korofin.dev"));
        aiUsageEventRepository.saveAndFlush(newEvent(owner, AiUsageEventType.CHAT, 500, true));

        // Ventana en el pasado, sin solapar con "ahora": el evento recién creado debe quedar fuera.
        Instant start = Instant.now().minusSeconds(600);
        Instant end = Instant.now().minusSeconds(300);
        List<AiUsageEventRepository.AiUsageEventTypeAggregate> aggregates =
                aiUsageEventRepository.aggregateByEventType(owner.getId(), start, end);

        assertThat(aggregates).isEmpty();
    }

    @Test
    void findAllByUser_IdAndCreatedAtBetweenScopesToOwnerAndRange() {
        User owner = userRepository.saveAndFlush(newUser("usage-range@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("usage-range-other@korofin.dev"));
        aiUsageEventRepository.saveAndFlush(newEvent(owner, AiUsageEventType.CHAT, 10, true));
        aiUsageEventRepository.saveAndFlush(newEvent(other, AiUsageEventType.CHAT, 10, true));

        List<AiUsageEvent> events = aiUsageEventRepository.findAllByUser_IdAndCreatedAtBetween(
                owner.getId(), Instant.now().minusSeconds(60), Instant.now().plusSeconds(60)
        );

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getUser().getId()).isEqualTo(owner.getId());
    }

    private AiUsageEvent newEvent(User owner, AiUsageEventType type, int tokens, boolean success) {
        AiUsageEvent event = new AiUsageEvent();
        event.setUser(owner);
        event.setProvider("groq");
        event.setEventType(type);
        event.setTokensUsed(tokens);
        event.setSuccess(success);
        return event;
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
