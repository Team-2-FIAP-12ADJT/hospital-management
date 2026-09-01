package com.fiap.hospital.history.projection.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class ProjectionFreshnessTest {

    @Test
    void gettersExposePersistedFields() throws Exception {
        Instant lastAppliedAt = Instant.parse("2026-08-25T12:00:00Z");
        ProjectionFreshness freshness = newInstance();
        setField(freshness, "id", (short) 1);
        setField(freshness, "lastAppliedAt", lastAppliedAt);

        assertEquals(lastAppliedAt, freshness.getLastAppliedAt());
    }

    private static ProjectionFreshness newInstance() throws Exception {
        var constructor = ProjectionFreshness.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
