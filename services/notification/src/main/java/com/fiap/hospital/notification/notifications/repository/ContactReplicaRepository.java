package com.fiap.hospital.notification.notifications.repository;

import com.fiap.hospital.notification.notifications.domain.ContactReplica;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactReplicaRepository extends JpaRepository<ContactReplica, UUID> {

    /**
     * A réplica é endereço de entrega, não registro: vale o evento mais recente,
     * e a guarda por {@code updated_at} descarta o que chegar fora de ordem.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO contact_replica (patient_id, email, phone, updated_at)
            VALUES (:patientId, :email, :phone, :updatedAt)
            ON CONFLICT (patient_id) DO UPDATE
               SET email      = EXCLUDED.email,
                   phone      = EXCLUDED.phone,
                   updated_at = EXCLUDED.updated_at
             WHERE contact_replica.updated_at <= EXCLUDED.updated_at
            """,
        nativeQuery = true
    )
    int upsert(
        @Param("patientId") UUID patientId,
        @Param("email") String email,
        @Param("phone") String phone,
        @Param("updatedAt") Instant updatedAt
    );
}
