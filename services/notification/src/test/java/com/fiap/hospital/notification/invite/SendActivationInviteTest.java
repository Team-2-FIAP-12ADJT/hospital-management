package com.fiap.hospital.notification.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.mail.enabled=false"
    }
)
@Testcontainers
class SendActivationInviteTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private AccountEventConsumer consumer;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetMailSender() {
        reset(mailSender);
    }

    @Test
    void eventoRepetidoNaoEnviaSegundoEmail() {
        UUID eventId = UUID.randomUUID();
        String envelope = AccountEventFixtures.userActivationRequested(
            eventId, UUID.randomUUID(), "3Yb9Qk2Lm7Rx0Tn5"
        );

        consumer.consume(envelope);
        consumer.consume(envelope);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(count(eventId)).isOne();
    }

    @Test
    void falhaDeSmtpNaoMarcaEventoProcessadoParaOKafkaRetentar() {
        UUID eventId = UUID.randomUUID();
        doThrow(new MailSendException("smtp down"))
            .when(mailSender)
            .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
            consumer.consume(AccountEventFixtures.userActivationRequested(
                eventId, UUID.randomUUID(), "tok"
            ))
        ).isInstanceOf(MailSendException.class);

        assertThat(count(eventId)).isZero();
    }

    @Test
    void enderecoInvalidoNaoInterrompeEnvioNemPerdeIdempotencia() {
        UUID eventId = UUID.randomUUID();
        doThrow(new MailParseException("invalid address"))
            .when(mailSender)
            .send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() ->
            consumer.consume(AccountEventFixtures.userActivationRequested(
                eventId, UUID.randomUUID(), "tok"
            ))
        );

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(count(eventId)).isOne();
    }

    @Test
    void addressExceptionEmbutidaNaoInterrompeEnvioNemPerdeIdempotencia() {
        UUID eventId = UUID.randomUUID();
        doThrow(new MailSendException("failed", new AddressException("bad local")))
            .when(mailSender)
            .send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() ->
            consumer.consume(AccountEventFixtures.userActivationRequested(
                eventId, UUID.randomUUID(), "tok"
            ))
        );

        assertThat(count(eventId)).isOne();
    }

    @Test
    void bounceSmtpNaoInterrompeEnvioNemPerdeIdempotencia() {
        UUID eventId = UUID.randomUUID();
        doThrow(new MailSendException(
            "failed",
            new SendFailedException("550 5.1.1 user unknown")
        ))
            .when(mailSender)
            .send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() ->
            consumer.consume(AccountEventFixtures.userActivationRequested(
                eventId, UUID.randomUUID(), "tok"
            ))
        );

        assertThat(count(eventId)).isOne();
    }

    @Test
    void falhaInesperadaNoMailerNaoInterrompeEnvioNemPerdeIdempotencia() {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalStateException("mailer exploded"))
            .when(mailSender)
            .send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() ->
            consumer.consume(AccountEventFixtures.userActivationRequested(
                eventId, UUID.randomUUID(), "tok"
            ))
        );

        assertThat(count(eventId)).isOne();
    }

    @Test
    void emailInvalidoNoEnvelopeNaoInterrompeConsumo() {
        UUID eventId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() ->
            consumer.consume(AccountEventFixtures.userActivationRequested(
                eventId, UUID.randomUUID(), "tok", "not-an-email"
            ))
        );

        verifyNoInteractions(mailSender);
        assertThat(count(eventId)).isZero();
    }

    @Test
    void ignoraTipoForaDoContratoSemGravarIdempotencia() {
        UUID eventId = UUID.randomUUID();

        consumer.consume(AccountEventFixtures.patientRegistered(eventId, UUID.randomUUID()));

        verifyNoInteractions(mailSender);
        assertThat(count(eventId)).isZero();
    }

    @Test
    void payloadInvalidoNaoTravaNemGravaIdempotencia() {
        long before = jdbcClient.sql("SELECT count(*) FROM processed_event")
            .query(Long.class)
            .single();

        assertThatNoException().isThrownBy(() -> consumer.consume("{not-json"));

        assertThat(jdbcClient.sql("SELECT count(*) FROM processed_event")
            .query(Long.class)
            .single()).isEqualTo(before);
    }

    private long count(UUID eventId) {
        return jdbcClient
            .sql("SELECT count(*) FROM processed_event WHERE event_id = :eventId")
            .param("eventId", eventId)
            .query(Long.class)
            .single();
    }
}
