package com.fiap.hospital.notification.notifications.consumer;

import com.fiap.hospital.notification.notifications.service.ScheduleAppointmentNotifications;
import com.fiap.hospital.notification.notifications.service.ScheduledAppointment;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final AppointmentEventParser parser;
    private final ScheduleAppointmentNotifications scheduleNotifications;

    AppointmentEventConsumer(
        AppointmentEventParser parser,
        ScheduleAppointmentNotifications scheduleNotifications
    ) {
        this.parser = parser;
        this.scheduleNotifications = scheduleNotifications;
    }

    @KafkaListener(topics = "hospital.appointment", groupId = "notification-appointment")
    public void receive(ConsumerRecord<String, String> record) {
        consume(record.value(), record.partition(), record.offset());
    }

    void consume(String envelopeJson) {
        consume(envelopeJson, -1, -1L);
    }

    private void consume(String envelopeJson, int partition, long offset) {
        ScheduledAppointment appointment;
        try {
            appointment = parser.parse(envelopeJson);
        } catch (UnsupportedEventException exception) {
            log.info("tipo fora da notificação de agendamento, ignorado: {}", exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            log.error(
                "discarding unparseable message on hospital.appointment, partition={} offset={}: {}",
                partition, offset, exception.getMessage()
            );
            return;
        }
        scheduleNotifications.schedule(appointment);
    }
}
