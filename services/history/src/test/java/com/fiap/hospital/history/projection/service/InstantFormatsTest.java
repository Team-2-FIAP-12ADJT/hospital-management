package com.fiap.hospital.history.projection.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstantFormatsTest {

    @Test
    void isoMillisReturnsNullWhenInstantIsNull() {
        assertNull(InstantFormats.isoMillis(null));
    }

    @Test
    void isoMillisTruncatesToMilliseconds() {
        Instant instant = Instant.parse("2026-07-10T14:00:00.123456789Z");
        assertEquals("2026-07-10T14:00:00.123Z", InstantFormats.isoMillis(instant));
    }

    @Test
    void privateConstructorExists() throws Exception {
        Constructor<InstantFormats> constructor = InstantFormats.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
