package com.fiap.hospital.notification.notifications.repository;

import com.fiap.hospital.notification.notifications.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findById(UUID id);

    // Sem filtro por `attempts`: quem decide elegibilidade é o ESTADO. A linha que
    // esgotou o teto sai de PENDING na mesma varredura que a esgotou, então filtrar
    // por tentativa aqui é redundante — e perigoso: baixar `maxAttempts` com fila
    // existente deixava linha em PENDING acima do novo teto, invisível para esta
    // consulta, sem estado terminal e sem nunca mais ser varrida.
    @Query("""
        SELECT n FROM Notification n
         WHERE n.status = com.fiap.hospital.notification.notifications.domain.NotificationStatus.PENDING
           AND n.fireAt <= :now
         ORDER BY n.fireAt ASC
        """)
    List<Notification> findDue(
        @Param("now") Instant now,
        Limit limit
    );

    /**
     * Só o pendente: lembrete já enviado não tem o que cancelar, e o índice
     * único garante que existe no máximo um por consulta.
     */
    @Query("""
        SELECT n FROM Notification n
         WHERE n.appointmentId = :appointmentId
           AND n.kind = com.fiap.hospital.notification.notifications.domain.NotificationKind.REMINDER
           AND n.status = com.fiap.hospital.notification.notifications.domain.NotificationStatus.PENDING
        """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findPendingReminder(@Param("appointmentId") UUID appointmentId);
}
