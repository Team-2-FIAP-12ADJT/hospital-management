package com.fiap.hospital.history.projection.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void processedEventCanBeConstructedByPersistence() throws Exception {
        var constructor = ProcessedEvent.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        org.junit.jupiter.api.Assertions.assertNotNull(constructor.newInstance());
    }
}
