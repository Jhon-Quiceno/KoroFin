package com.korofin.backend.repository.ai;

import com.korofin.backend.entity.ai.AiMessage;
import com.korofin.backend.entity.ai.AiMessageKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a persistencia de {@link AiMessage}, siempre delimitado por dueño.
 */
public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    /**
     * Usada por {@code AiChatService} tanto para {@code GET /api/ai/chat/history} como para
     * cargar la ventana de turnos recientes que se envía al proveedor de IA para continuidad de
     * conversación — las filas {@link AiMessageKind#INSIGHT} nunca deben filtrarse a ninguna de
     * las dos, de ahí el filtro por {@code kind}.
     */
    Page<AiMessage> findByUser_IdAndKindOrderByCreatedAtDesc(Long userId, AiMessageKind kind, Pageable pageable);

    Optional<AiMessage> findFirstByUser_IdAndKindOrderByCreatedAtDesc(Long userId, AiMessageKind kind);

    /**
     * Borra la conversación de chat del usuario en cada login nuevo (ver
     * {@code UserService#login}) para que cada sesión arranque el asistente con una conversación
     * en blanco. Delimitada a {@link AiMessageKind#CHAT} exclusivamente — las filas
     * {@link AiMessageKind#INSIGHT} (insights del dashboard) deben sobrevivir a un login.
     */
    @Modifying
    @Query("DELETE FROM AiMessage a WHERE a.user.id = :userId AND a.kind = :kind")
    int deleteByUserIdAndKind(@Param("userId") Long userId, @Param("kind") AiMessageKind kind);
}
