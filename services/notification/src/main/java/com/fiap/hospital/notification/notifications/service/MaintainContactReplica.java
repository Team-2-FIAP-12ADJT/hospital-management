package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
import com.fiap.hospital.notification.notifications.repository.ContactReplicaRepository;
import org.springframework.stereotype.Service;

@Service
public class MaintainContactReplica {

    public static final String CONSUMER = "contact";

    private final IdempotencyService idempotencyService;
    private final ContactReplicaRepository contacts;

    public MaintainContactReplica(
        IdempotencyService idempotencyService,
        ContactReplicaRepository contacts
    ) {
        this.idempotencyService = idempotencyService;
        this.contacts = contacts;
    }

    public void apply(PatientContact contact) {
        idempotencyService.process(CONSUMER, contact.eventId(), () ->
            contacts.upsert(
                contact.patientId(),
                contact.email(),
                contact.phone(),
                contact.occurredAt()
            )
        );
    }
}
