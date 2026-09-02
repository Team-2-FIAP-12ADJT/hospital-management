package com.fiap.hospital.notification.idempotency;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventRepository repository;

    @Mock
    private Runnable effect;

    @Test
    void runsEffectWhenEventIsInserted() {
        UUID eventId = UUID.randomUUID();
        when(repository.insertIfAbsent(eventId)).thenReturn(1);

        new IdempotencyService(repository).process(eventId, effect);

        verify(effect).run();
    }

    @Test
    void skipsEffectWhenEventAlreadyExists() {
        UUID eventId = UUID.randomUUID();
        when(repository.insertIfAbsent(eventId)).thenReturn(0);

        new IdempotencyService(repository).process(eventId, effect);

        verifyNoInteractions(effect);
    }
}
