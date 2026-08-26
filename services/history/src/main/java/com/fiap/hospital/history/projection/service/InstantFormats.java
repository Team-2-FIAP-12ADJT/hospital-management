package com.fiap.hospital.history.projection.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class InstantFormats {

    private InstantFormats() {
    }

    static String isoMillis(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
