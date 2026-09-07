package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationKind;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.repository.ContactReplicaRepository;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import com.fiap.hospital.notification.notifications.service.NotificationDispatcher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "notification.sweep-interval=PT1H",
    "notification.reminder-lead-time=PT24H",
    "notification.max-attempts=3"
})
@Testcontainers
class AppointmentNotificationsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");

    @Autowired
    private AppointmentEventConsumer appointmentConsumer;

    @Autowired
    private PatientContactConsumer contactConsumer;

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ContactReplicaRepository contacts;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private MutableClock clock;

    private static final UUID SEEDED_PATIENT =
        UUID.fromString("00000000-0000-4000-8000-000000000003");

    @BeforeEach
    void reset() {
        notifications.deleteAll();
        mailSender.sent().clear();
        clock.set(NOW);
    }

    @Test
    void theSeededPatientIsReachableWithoutEverHavingBeenRegisteredByEvent() {
        assertThat(contacts.findById(SEEDED_PATIENT))
            .as("a conta de demonstração nasce por migração; sem semente na réplica não há e-mail")
            .isPresent();

        scheduleAppointment(UUID.randomUUID(), SEEDED_PATIENT);
        dispatcher.sweep();

        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().getFirst().getTo())
            .containsExactly("marcos.vieira@exemplo.com");
        assertThat(statusOf(NotificationKind.CONFIRMATION)).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void confirmationReachesTheReplicaAddressAndTheReminderWaitsForItsTurn() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        scheduleAppointment(appointmentId, patientId);

        dispatcher.sweep();

        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().getFirst().getTo()).containsExactly("marcos@exemplo.com");
        assertThat(mailSender.sent().getFirst().getSubject()).isEqualTo("Consulta agendada");
        assertThat(statusOf(NotificationKind.CONFIRMATION)).isEqualTo(NotificationStatus.SENT);
        assertThat(statusOf(NotificationKind.REMINDER)).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void reminderIsSentOnlyOnceTheLeadTimeIsReached() {
        UUID patientId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        scheduleAppointment(UUID.randomUUID(), patientId);
        dispatcher.sweep();
        mailSender.sent().clear();

        clock.set(EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(24)));
        dispatcher.sweep();

        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().getFirst().getSubject())
            .isEqualTo("Lembrete: sua consulta está próxima");
        assertThat(statusOf(NotificationKind.REMINDER)).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void reminderUsesTheCorrectedAddressAndNotTheOneFromRegistration() {
        UUID patientId = UUID.randomUUID();
        registerContact(patientId, "antigo@exemplo.com");
        scheduleAppointment(UUID.randomUUID(), patientId);
        contactConsumer.receive(record("hospital.person", EventFixtures.patientContactUpdated(
            UUID.randomUUID(), patientId, "novo@exemplo.com",
            EventFixtures.OCCURRED_AT.plusSeconds(60)
        )));

        clock.set(EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(24)));
        dispatcher.sweep();

        assertThat(mailSender.sent())
            .as("o lembrete sai para o endereço corrigido, não para o do cadastro")
            .allSatisfy(message -> assertThat(message.getTo()).containsExactly("novo@exemplo.com"));
    }

    @Test
    void notificationStaysPendingWhileTheContactReplicaHasNotArrived() {
        scheduleAppointment(UUID.randomUUID(), UUID.randomUUID());

        dispatcher.sweep();

        assertThat(mailSender.sent()).isEmpty();
        Notification confirmation = byKind(NotificationKind.CONFIRMATION);
        assertThat(confirmation.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(confirmation.getAttempts()).isEqualTo((short) 1);
    }

    @Test
    void aNotificationStopsBeingSweptOnceItHitsTheAttemptCap() {
        scheduleAppointment(UUID.randomUUID(), UUID.randomUUID());

        for (int attempt = 0; attempt < 5; attempt++) {
            dispatcher.sweep();
        }

        assertThat(byKind(NotificationKind.CONFIRMATION).getAttempts())
            .as("o teto de tentativas para a varredura em vez de tentar para sempre")
            .isEqualTo((short) 3);
    }

    @Test
    void redeliveryOfTheSameEventDoesNotDuplicateConfirmationNorReminder() {
        UUID eventId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String envelope = EventFixtures.appointmentScheduled(eventId, appointmentId, patientId);

        appointmentConsumer.receive(record("hospital.appointment", envelope));
        appointmentConsumer.receive(record("hospital.appointment", envelope));

        assertThat(notifications.findAll()).hasSize(2);
    }

    @Test
    void anOlderContactUpdateDoesNotOverwriteANewerAddress() {
        UUID patientId = UUID.randomUUID();
        registerContact(patientId, "atual@exemplo.com");

        contactConsumer.receive(record("hospital.person", EventFixtures.patientContactUpdated(
            UUID.randomUUID(), patientId, "atrasado@exemplo.com",
            EventFixtures.OCCURRED_AT.minusSeconds(60)
        )));

        assertThat(contacts.findById(patientId).orElseThrow().getEmail())
            .isEqualTo("atual@exemplo.com");
    }

    @Test
    void appointmentInsideTheLeadTimeGetsAConfirmationAndNoReminder() {
        UUID patientId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        clock.set(EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(1)));

        scheduleAppointment(UUID.randomUUID(), patientId);

        assertThat(notifications.findAll())
            .extracting(Notification::getKind)
            .containsExactly(NotificationKind.CONFIRMATION);
    }

    @Test
    void reschedulingSwapsTheReminderWithoutTrippingTheUniqueIndex() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        scheduleAppointment(appointmentId, patientId);
        Instant newSlot = EventFixtures.SCHEDULED_AT.plus(Duration.ofDays(2));

        appointmentConsumer.receive(record("hospital.appointment", EventFixtures
            .appointmentRescheduled(
                UUID.randomUUID(), appointmentId, patientId,
                EventFixtures.SCHEDULED_AT, newSlot
            )));

        var reminders = notifications.findAll().stream()
            .filter(n -> n.getKind() == NotificationKind.REMINDER)
            .toList();
        assertThat(reminders)
            .as("um lembrete cancelado e um pendente, nunca dois pendentes")
            .hasSize(2);
        assertThat(reminders).anySatisfy(reminder -> {
            assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(reminder.getScheduledAt()).isEqualTo(EventFixtures.SCHEDULED_AT);
        });
        assertThat(reminders).anySatisfy(reminder -> {
            assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(reminder.getFireAt()).isEqualTo(newSlot.minus(Duration.ofHours(24)));
        });
    }

    @Test
    void theSwappedReminderFiresForTheNewTimeAndNotTheOld() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        scheduleAppointment(appointmentId, patientId);
        Instant newSlot = EventFixtures.SCHEDULED_AT.plus(Duration.ofDays(2));
        appointmentConsumer.receive(record("hospital.appointment", EventFixtures
            .appointmentRescheduled(
                UUID.randomUUID(), appointmentId, patientId,
                EventFixtures.SCHEDULED_AT, newSlot
            )));
        mailSender.sent().clear();

        clock.set(EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(24)));
        dispatcher.sweep();

        assertThat(mailSender.sent())
            .as("no horário do lembrete antigo nada dispara — ele foi cancelado")
            .noneSatisfy(message -> assertThat(message.getSubject())
                .isEqualTo("Lembrete: sua consulta está próxima"));

        clock.set(newSlot.minus(Duration.ofHours(24)));
        dispatcher.sweep();

        assertThat(mailSender.sent())
            .anySatisfy(message -> assertThat(message.getSubject())
                .isEqualTo("Lembrete: sua consulta está próxima"));
    }

    @Test
    void cancellingAnAppointmentCancelsThePendingReminder() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        scheduleAppointment(appointmentId, patientId);

        appointmentConsumer.receive(record("hospital.appointment",
            EventFixtures.appointmentCancelled(UUID.randomUUID(), appointmentId, patientId)));

        assertThat(statusOf(NotificationKind.REMINDER)).isEqualTo(NotificationStatus.CANCELLED);

        mailSender.sent().clear();
        clock.set(EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(24)));
        dispatcher.sweep();

        assertThat(mailSender.sent())
            .as("lembrete cancelado não é varrido; a confirmação pendente sai normalmente")
            .noneSatisfy(message -> assertThat(message.getSubject())
                .isEqualTo("Lembrete: sua consulta está próxima"));
    }

    @Test
    void cancellingAfterTheReminderWentOutIsSilent() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");
        scheduleAppointment(appointmentId, patientId);
        clock.set(EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(24)));
        dispatcher.sweep();
        assertThat(statusOf(NotificationKind.REMINDER)).isEqualTo(NotificationStatus.SENT);

        appointmentConsumer.receive(record("hospital.appointment",
            EventFixtures.appointmentCancelled(UUID.randomUUID(), appointmentId, patientId)));

        assertThat(statusOf(NotificationKind.REMINDER))
            .as("lembrete já enviado não tem o que cancelar, e isso não é erro")
            .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void appointmentCompletedIsNotConsumedHere() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        registerContact(patientId, "marcos@exemplo.com");

        appointmentConsumer.receive(record("hospital.appointment",
            EventFixtures.appointmentCompleted(UUID.randomUUID(), appointmentId, patientId)));

        assertThat(notifications.findAll())
            .as("AppointmentCompleted é só do history")
            .isEmpty();
    }

    @Test
    void refusesASecondPendingReminderForTheSameAppointment() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        notifications.saveAndFlush(pendingReminder(appointmentId, patientId));

        assertThatThrownBy(() -> notifications.saveAndFlush(pendingReminder(appointmentId, patientId)))
            .as("no máximo um lembrete pendente por consulta")
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Notification pendingReminder(UUID appointmentId, UUID patientId) {
        return Notification.reminder(
            UUID.randomUUID(), patientId, appointmentId, EventFixtures.SCHEDULED_AT,
            "Dra. Helena Prado", "Cardiologia",
            EventFixtures.SCHEDULED_AT.minus(Duration.ofHours(24)), NOW
        ).orElseThrow();
    }

    private void registerContact(UUID patientId, String email) {
        contactConsumer.receive(record(
            "hospital.person",
            EventFixtures.patientRegistered(UUID.randomUUID(), patientId, email)
        ));
    }

    private void scheduleAppointment(UUID appointmentId, UUID patientId) {
        appointmentConsumer.receive(record(
            "hospital.appointment",
            EventFixtures.appointmentScheduled(UUID.randomUUID(), appointmentId, patientId)
        ));
    }

    private static ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, null, value);
    }

    private NotificationStatus statusOf(NotificationKind kind) {
        return byKind(kind).getStatus();
    }

    private Notification byKind(NotificationKind kind) {
        return notifications.findAll().stream()
            .filter(notification -> notification.getKind() == kind)
            .findFirst()
            .orElseThrow(() -> new AssertionError("nenhuma notificação do tipo " + kind));
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static final class RecordingMailSender implements MailSender {

        private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

        List<SimpleMailMessage> sent() {
            return sent;
        }

        @Override
        public void send(SimpleMailMessage message) {
            sent.add(message);
        }

        @Override
        public void send(SimpleMailMessage... messages) {
            for (SimpleMailMessage message : messages) {
                send(message);
            }
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current = new AtomicReference<>(NOW);

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
