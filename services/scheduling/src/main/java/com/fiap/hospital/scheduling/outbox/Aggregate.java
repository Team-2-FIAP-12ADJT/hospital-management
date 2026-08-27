package com.fiap.hospital.scheduling.outbox;

public enum Aggregate {
    PERSON("person", "hospital.person"),
    APPOINTMENT("appointment", "hospital.appointment");

    private final String type;
    private final String topic;

    Aggregate(String type, String topic) {
        this.type = type;
        this.topic = topic;
    }

    public String type() {
        return type;
    }

    public String topic() {
        return topic;
    }
}
