package com.fiap.hospital.identity.outbox;

public enum Aggregate {
    ACCOUNT("account", "hospital.account");

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
