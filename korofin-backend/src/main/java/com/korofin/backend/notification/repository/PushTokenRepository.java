package com.korofin.backend.notification.repository;

import com.korofin.backend.notification.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a persistencia de {@link PushToken}, siempre delimitado por dueño.
 */
public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    List<PushToken> findByUser_Id(Long userId);

    Optional<PushToken> findByUser_IdAndDeviceId(Long userId, String deviceId);

    /**
     * Borra a lo sumo una fila, delimitada por dueño para que un cliente nunca pueda borrar el
     * token de otro usuario adivinando un {@code deviceId}. {@code @Modifying} en vez de un
     * método de borrado derivado para que corra como una sola sentencia y quien llama pueda
     * verificar la cantidad de filas afectadas.
     *
     * @param userId   dueño del token
     * @param deviceId identificador del dispositivo a borrar
     * @return cantidad de filas borradas (0 o 1)
     */
    @Modifying
    @Query("DELETE FROM PushToken p WHERE p.user.id = :userId AND p.deviceId = :deviceId")
    int deleteByUser_IdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);
}
