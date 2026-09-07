package com.fiap.hospital.scheduling.outbox;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class MillisecondInstantSerializer extends ValueSerializer<Instant> {
    // O default do Jackson omite zeros e pode publicar mais precisao que o contrato permite.

    private static final DateTimeFormatter FORMATTER =
        new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    @Override
    public void serialize(
        Instant value,
        JsonGenerator gen,
        SerializationContext ctxt
    ) {
        gen.writeString(FORMATTER.format(value));
    }
}
