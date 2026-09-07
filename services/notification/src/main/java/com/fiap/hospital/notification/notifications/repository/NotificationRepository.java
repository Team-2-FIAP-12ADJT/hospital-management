package com.fiap.hospital.notification.notifications.repository;

import com.fiap.hospital.notification.notifications.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
        SELECT n FROM Notification n
         WHERE n.status = com.fiap.hospital.notification.notifications.domain.NotificationStatus.PENDING
           AND n.fireAt <= :now
           AND n.attempts < :maxAttempts
         ORDER BY n.fireAt ASC
        """)
    List<Notification> findDue(
        @Param("now") Instant now,
        @Param("maxAttempts") short maxAttempts,
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
    Optional<Notification> findPendingReminder(@Param("appointmentId") UUID appointmentId);
}
