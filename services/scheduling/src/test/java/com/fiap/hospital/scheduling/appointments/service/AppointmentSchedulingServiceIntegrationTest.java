package com.fiap.hospital.scheduling.appointments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.domain.AppointmentStatus;
import com.fiap.hospital.scheduling.appointments.repository.AppointmentRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class AppointmentSchedulingServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_DOCTOR = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final Instant BASE = Instant.parse("2030-01-01T12:00:00Z");

    @Autowired
    private AppointmentSchedulingService service;

    @Autowired
    private AppointmentRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        jdbc.sql("""
            INSERT INTO participants.doctor
                (id, tax_identifier, crm, specialty, name, email)
            VALUES (:id, :tax, :crm, 'General', 'Other Doctor', 'other@hospital.local')
            ON CONFLICT (id) DO NOTHING
            """)
            .param("id", OTHER_DOCTOR)
            .param("tax", "39053344706")
            .param("crm", "CRM-SP 654322")
            .update();
    }

    @Test
    void occupation_rules_cover_normal_fit_in_cancelled_completed_and_other_doctor() {
        Instant slot = BASE.plusSeconds(3600);
        Appointment normal = schedule(DOCTOR, slot, false, null);
        assertConflict(() -> schedule(DOCTOR, slot, false, null));
        assertThat(schedule(OTHER_DOCTOR, slot, false, null).getDoctorId())
            .isEqualTo(OTHER_DOCTOR);

        Appointment fitInAtOccupiedSlot = schedule(DOCTOR, slot, true, "urgent");
        assertConflict(() -> schedule(DOCTOR, slot, false, null));
        service.cancel(normal.getId());
        service.cancel(fitInAtOccupiedSlot.getId());
        assertThat(schedule(DOCTOR, slot, false, null).getStatus())
            .isEqualTo(AppointmentStatus.SCHEDULED);

        Appointment completed = schedule(DOCTOR, slot.plusSeconds(1), false, null);
        service.complete(completed.getId());
        assertConflict(() -> schedule(DOCTOR, slot.plusSeconds(1), false, null));

        Appointment fitIn = schedule(DOCTOR, slot.plusSeconds(2), true, "urgent");
        assertConflict(() -> schedule(DOCTOR, slot.plusSeconds(2), false, null));
        assertThat(schedule(DOCTOR, slot.plusSeconds(2), true, "second urgent"))
            .isNotNull();
        assertThat(repository.findById(fitIn.getId())).isPresent();
    }

    @Test
    void rescheduling_excludes_own_row_and_sub_microsecond_times_collide() {
        Instant slot = BASE.plusSeconds(7200);
        Appointment appointment = schedule(DOCTOR, slot, false, null);
        service.reschedule(appointment.getId(), slot, false, null);
        assertThat(repository.findById(appointment.getId()).orElseThrow().getScheduledAt())
            .isEqualTo(slot);
        service.reschedule(appointment.getId(), slot.plusSeconds(1), false, null);
        assertThat(repository.findById(appointment.getId()).orElseThrow().getScheduledAt())
            .isEqualTo(slot.plusSeconds(1));

        Instant precise = BASE.plusSeconds(9000).plusNanos(123456789);
        schedule(DOCTOR, precise, false, null);
        assertConflict(() -> schedule(
            DOCTOR, precise.plusNanos(100), false, null
        ));
    }

    @Test
    void v1_mapping_and_each_foreign_key_are_enforced_by_postgresql() {
        assertThat(jdbc.sql("""
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'scheduling' AND table_name = 'appointment'
              AND column_name = 'scheduled_at'
            """).query(String.class).single()).isEqualTo("timestamp with time zone");
        assertThat(jdbc.sql("""
            SELECT count(*) FROM pg_constraint
            WHERE conrelid = 'scheduling.appointment'::regclass
              AND contype = 'c'
              AND pg_get_constraintdef(oid) LIKE '%SCHEDULED%'
            """).query(Long.class).single()).isEqualTo(1L);

        UUID invalidPatient = UUID.randomUUID();
        assertConstraint("appointment_patient_id_fkey", () ->
            scheduleWithIds(invalidPatient, DOCTOR, BASE.plusSeconds(10000)));

        UUID invalidDoctor = UUID.randomUUID();
        assertConstraint("appointment_doctor_id_fkey", () ->
            scheduleWithIds(PATIENT, invalidDoctor, BASE.plusSeconds(10001)));
    }

    @Test
    void transaction_rolls_back_after_flush_and_failure() {
        UUID id = UUID.randomUUID();
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            Appointment appointment = Appointment.schedule(
                id, PATIENT, DOCTOR, BASE.plusSeconds(11000),
                false, null, BASE, false
            );
            repository.saveAndFlush(appointment);
            assertThat(jdbc.sql("SELECT count(*) FROM scheduling.appointment WHERE id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(1L);
            throw new RollbackMarker();
        })).isInstanceOf(RollbackMarker.class);

        assertThat(jdbc.sql("SELECT count(*) FROM scheduling.appointment WHERE id = :id")
            .param("id", id).query(Long.class).single()).isZero();
    }

    @Test
    void concurrent_creations_same_slot_have_one_success_and_one_conflict() throws Exception {
        Instant slot = BASE.plusSeconds(12000);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockReady = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate().executeWithoutResult(status -> {
                repository.acquireDoctorScheduleLock(DOCTOR);
                lockReady.countDown();
                await(releaseLock);
            }));
            assertThat(lockReady.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch submitted = new CountDownLatch(2);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    submitted.countDown();
                    try {
                        schedule(DOCTOR, slot, false, null);
                        return true;
                    } catch (IllegalStateException ex) {
                        return false;
                    }
                }));
            }
            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            assertFutureWaitsForLock(results.get(0));
            assertFutureWaitsForLock(results.get(1));
            releaseLock.countDown();
            assertThat(results.stream().map(this::get).toList())
                .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.sql("""
                SELECT count(*) FROM scheduling.appointment
                WHERE doctor_id = :doctor AND scheduled_at = :slot
                """)
                .param("doctor", DOCTOR)
                .param("slot", Timestamp.from(slot))
                .query(Long.class)
                .single()).isEqualTo(1L);
            assertThat(jdbc.sql("""
                SELECT status FROM scheduling.appointment
                WHERE doctor_id = :doctor AND scheduled_at = :slot
                """)
                .param("doctor", DOCTOR)
                .param("slot", Timestamp.from(slot))
                .query(String.class)
                .single()).isEqualTo("SCHEDULED");
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void fit_in_and_normal_compete_in_both_acquisition_orders() throws Exception {
        assertFitInOrder(true);
        assertFitInOrder(false);
    }

    @Test
    void concurrent_reschedules_have_one_success_and_one_conflict() throws Exception {
        Appointment first = schedule(DOCTOR, BASE.plusSeconds(13000), false, null);
        Appointment second = schedule(DOCTOR, BASE.plusSeconds(13001), false, null);
        Instant target = BASE.plusSeconds(13002);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = rescheduleConcurrently(first.getId(), target, ready, start, executor);
            Future<Boolean> two = rescheduleConcurrently(second.getId(), target, ready, start, executor);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(get(one), get(two)))
                .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.sql("""
                SELECT count(*) FROM scheduling.appointment
                WHERE doctor_id = :doctor AND scheduled_at = :slot
                """)
                .param("doctor", DOCTOR)
                .param("slot", Timestamp.from(target))
                .query(Long.class)
                .single()).isEqualTo(1L);
            assertThat(jdbc.sql("""
                SELECT count(*) FROM scheduling.appointment
                WHERE doctor_id = :doctor
                  AND scheduled_at IN (:first, :second)
                """)
                .param("doctor", DOCTOR)
                .param("first", Timestamp.from(BASE.plusSeconds(13000)))
                .param("second", Timestamp.from(BASE.plusSeconds(13001)))
                .query(Long.class)
                .single()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrent_cancel_and_complete_and_double_complete_have_single_transitions() throws Exception {
        Appointment appointment = schedule(DOCTOR, BASE.plusSeconds(14000), false, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> cancel = executor.submit(() -> {
                ready.countDown(); await(start);
                try { service.cancel(appointment.getId()); return true; }
                catch (IllegalStateException ex) { return false; }
            });
            Future<Boolean> complete = executor.submit(() -> {
                ready.countDown(); await(start);
                try { return service.complete(appointment.getId()); }
                catch (IllegalStateException ex) { return false; }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean cancelSucceeded = get(cancel);
            boolean completeSucceeded = get(complete);
            assertThat(List.of(cancelSucceeded, completeSucceeded))
                .containsExactlyInAnyOrder(true, false);
            AppointmentStatus finalStatus = repository.findById(appointment.getId())
                .orElseThrow()
                .getStatus();
            assertThat(finalStatus).isEqualTo(
                cancelSucceeded ? AppointmentStatus.CANCELLED : AppointmentStatus.COMPLETED
            );

            Appointment second = schedule(DOCTOR, BASE.plusSeconds(14001), false, null);
            CountDownLatch completeReady = new CountDownLatch(2);
            CountDownLatch completeStart = new CountDownLatch(1);
            Future<Boolean> first = completeConcurrently(second.getId(), completeReady, completeStart, executor);
            Future<Boolean> secondResult = completeConcurrently(second.getId(), completeReady, completeStart, executor);
            assertThat(completeReady.await(5, TimeUnit.SECONDS)).isTrue();
            completeStart.countDown();
            assertThat(List.of(get(first), get(secondResult)))
                .containsExactlyInAnyOrder(true, false);
            assertThat(repository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<Boolean> rescheduleConcurrently(
        UUID id, Instant target, CountDownLatch ready, CountDownLatch start, ExecutorService executor
    ) {
        return executor.submit(() -> {
            ready.countDown(); await(start);
            try {
                service.reschedule(id, target, false, null);
                return true;
            } catch (IllegalStateException ex) {
                return false;
            }
        });
    }

    private Future<Boolean> completeConcurrently(
        UUID id, CountDownLatch ready, CountDownLatch start, ExecutorService executor
    ) {
        return executor.submit(() -> {
            ready.countDown(); await(start);
            return service.complete(id);
        });
    }

    private Appointment schedule(UUID doctor, Instant slot, boolean fitIn, String reason) {
        return service.schedule(PATIENT, doctor, slot, fitIn, reason);
    }

    private void scheduleWithIds(UUID patient, UUID doctor, Instant slot) {
        service.schedule(patient, doctor, slot, false, null);
    }

    private void assertConflict(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(IllegalStateException.class);
    }

    private void assertConstraint(String constraint, ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown)
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("participant does not exist");
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof DataIntegrityViolationException)) {
            cause = cause.getCause();
        }
        assertThat(cause).isNotNull();
        assertThat(cause.getCause()).hasMessageContaining(constraint);
    }

    private boolean get(Future<Boolean> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException(ex.getCause());
        } catch (java.util.concurrent.TimeoutException ex) {
            throw new AssertionError("timed out waiting for task", ex);
        }
    }

    private void assertFutureWaitsForLock(Future<Boolean> future) {
        assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    private void assertFitInOrder(boolean fitInFirst) throws Exception {
        Instant slot = BASE.plusSeconds(fitInFirst ? 15000 : 15001);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Future<Boolean>> concurrent = new ArrayList<>();
        try {
            // A primeira operacao roda dentro de uma transacao que permanece ABERTA: ela ja
            // segura o advisory lock do medico e ja gravou, mas ainda nao commitou. A segunda
            // e submetida dentro dessa janela, entao disputa o lock de verdade em vez de
            // apenas ler uma linha ja commitada.
            transactionTemplate().executeWithoutResult(status -> {
                schedule(DOCTOR, slot, fitInFirst, fitInFirst ? "urgent" : null);
                Future<Boolean> second = executor.submit(() -> {
                    try {
                        schedule(DOCTOR, slot, !fitInFirst, fitInFirst ? null : "urgent");
                        return true;
                    } catch (IllegalStateException ex) {
                        return false;
                    }
                });
                concurrent.add(second);
                assertFutureWaitsForLock(second);
            });

            // Encaixe primeiro: a normal seguinte encontra o horario ocupado e recusa.
            // Normal primeiro: o encaixe seguinte e autorizado e entra no mesmo horario.
            assertThat(get(concurrent.get(0))).isEqualTo(!fitInFirst);
            assertThat(jdbc.sql("""
                SELECT count(*) FROM scheduling.appointment
                WHERE doctor_id = :doctor AND scheduled_at = :slot
                """)
                .param("doctor", DOCTOR)
                .param("slot", Timestamp.from(slot))
                .query(Long.class)
                .single()).isEqualTo(fitInFirst ? 1L : 2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void fit_in_flag_reason_and_cancellation_are_persisted() {
        Instant slot = BASE.plusSeconds(17000);
        Appointment fitIn = schedule(DOCTOR, slot, true, "urgent case");

        assertThat(column(fitIn.getId(), "fit_in", Boolean.class)).isTrue();
        assertThat(column(fitIn.getId(), "fit_in_reason", String.class)).isEqualTo("urgent case");
        assertThat(column(fitIn.getId(), "cancelled_at", Timestamp.class)).isNull();

        service.cancel(fitIn.getId());

        assertThat(column(fitIn.getId(), "status", String.class)).isEqualTo("CANCELLED");
        assertThat(column(fitIn.getId(), "cancelled_at", Timestamp.class)).isNotNull();
        assertThat(column(fitIn.getId(), "fit_in_reason", String.class)).isEqualTo("urgent case");
    }

    private <T> T column(UUID id, String name, Class<T> type) {
        return jdbc.sql("SELECT " + name + " FROM scheduling.appointment WHERE id = :id")
            .param("id", id)
            .query(type)
            .optional()
            .orElse(null);
    }

    @Test
    void unknown_appointment_id_is_not_found_in_internal_operations() {
        UUID missing = UUID.randomUUID();
        assertNotFound(() -> service.reschedule(missing, BASE.plusSeconds(16000), false, null));
        assertNotFound(() -> service.cancel(missing));
        assertNotFound(() -> service.complete(missing));
    }

    private void assertNotFound(ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    private void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for latch");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private static final class RollbackMarker extends RuntimeException {}
}
