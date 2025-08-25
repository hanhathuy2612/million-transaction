package com.hnh.example.transaction_example.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hnh.example.transaction_example.domain.OutboxEvent;
import com.hnh.example.transaction_example.domain.Payment;
import com.hnh.example.transaction_example.repository.OutboxEventRepository;
import com.hnh.example.transaction_example.service.OutboxService;
import com.hnh.example.transaction_example.testutils.TestDataBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Service Tests")
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxService outboxService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testPayment = TestDataBuilder.payment().build();
    }

    @Nested
    @DisplayName("Publish Payment Events Tests")
    class PublishPaymentEventsTests {

        @Test
        @DisplayName("Should publish payment authorized event successfully")
        void shouldPublishPaymentAuthorizedEventSuccessfully() {
            // Arrange
            Payment payment = TestDataBuilder.payment().build();
            OutboxEvent savedEvent = OutboxEvent.paymentAuthorized(payment.getId(), "{}");

            when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedEvent);

            // Act
            outboxService.publishPaymentAuthorized(payment);

            // Assert
            verify(outboxEventRepository).save(argThat(event -> event.getEventType().equals("payment.authorized") &&
                    event.getAggregateId().equals(payment.getId()) &&
                    event.getAggregateType().equals("Payment") &&
                    event.getPayload() != null));
        }

        @Test
        @DisplayName("Should publish payment captured event successfully")
        void shouldPublishPaymentCapturedEventSuccessfully() {
            // Arrange
            Payment payment = TestDataBuilder.payment().build();
            OutboxEvent savedEvent = OutboxEvent.paymentCaptured(payment.getId(), "{}");

            when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedEvent);

            // Act
            outboxService.publishPaymentCaptured(payment, BigDecimal.valueOf(100.00));

            // Assert
            verify(outboxEventRepository).save(argThat(event -> event.getEventType().equals("payment.captured") &&
                    event.getAggregateId().equals(payment.getId()) &&
                    event.getAggregateType().equals("Payment") &&
                    event.getPayload() != null));
        }

        @Test
        @DisplayName("Should handle repository errors gracefully")
        void shouldHandleRepositoryErrorsGracefully() {
            // Arrange
            Payment payment = TestDataBuilder.payment().build();
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // Act & Assert
            assertThatThrownBy(() -> outboxService.publishPaymentAuthorized(payment))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to serialize event payload");

            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }
    }

    @Nested
    @DisplayName("Payment Event Publishing Tests")
    class PaymentEventPublishingTests {

        @Test
        @DisplayName("Should publish payment refunded event successfully")
        void shouldPublishPaymentRefundedEventSuccessfully() {
            // Arrange
            Payment payment = TestDataBuilder.payment().build();
            OutboxEvent savedEvent = OutboxEvent.paymentRefunded(payment.getId(), "{}");
            when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedEvent);

            // Act
            outboxService.publishPaymentRefunded(payment, BigDecimal.valueOf(50.00));

            // Assert
            verify(outboxEventRepository).save(argThat(event -> event.getEventType().equals("payment.refunded") &&
                    event.getAggregateId().equals(payment.getId()) &&
                    event.getAggregateType().equals("Payment") &&
                    event.getPayload() != null));
        }

        @Test
        @DisplayName("Should publish payment failed event successfully")
        void shouldPublishPaymentFailedEventSuccessfully() {
            // Arrange
            Payment payment = TestDataBuilder.payment().build();
            OutboxEvent savedEvent = OutboxEvent.paymentFailed(payment.getId(), "{}");
            when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(savedEvent);

            // Act
            outboxService.publishPaymentFailed(payment, "Insufficient funds");

            // Assert
            verify(outboxEventRepository).save(argThat(event -> event.getEventType().equals("payment.failed") &&
                    event.getAggregateId().equals(payment.getId()) &&
                    event.getAggregateType().equals("Payment") &&
                    event.getPayload() != null));
        }
    }

    @Nested
    @DisplayName("Event Management Tests")
    class EventManagementTests {

        @Test
        @DisplayName("Should get unpublished events with limit")
        void shouldGetUnpublishedEventsWithLimit() {
            // Arrange
            List<OutboxEvent> events = List.of(
                    OutboxEvent.paymentAuthorized(UUID.randomUUID(), "{}"),
                    OutboxEvent.paymentCaptured(UUID.randomUUID(), "{}"));
            when(outboxEventRepository.findUnpublishedEventsWithLimit(5)).thenReturn(events);

            // Act
            List<OutboxEvent> result = outboxService.getUnpublishedEvents(5);

            // Assert
            assertThat(result).hasSize(2);
            verify(outboxEventRepository).findUnpublishedEventsWithLimit(5);
        }

        @Test
        @DisplayName("Should mark events as published")
        void shouldMarkEventsAsPublished() {
            // Arrange
            List<Long> eventIds = List.of(1L, 2L, 3L);

            // Act
            outboxService.markEventsAsPublished(eventIds);

            // Assert
            verify(outboxEventRepository).markAsPublished(eventIds, any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should get unpublished event count")
        void shouldGetUnpublishedEventCount() {
            // Arrange
            when(outboxEventRepository.countUnpublishedEvents()).thenReturn(10L);

            // Act
            Long count = outboxService.getUnpublishedEventCount();

            // Assert
            assertThat(count).isEqualTo(10L);
            verify(outboxEventRepository).countUnpublishedEvents();
        }

        @Test
        @DisplayName("Should cleanup old events")
        void shouldCleanupOldEvents() {
            // Arrange
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

            // Act
            outboxService.cleanupOldEvents(cutoffDate);

            // Assert
            verify(outboxEventRepository).deleteOldPublishedEvents(cutoffDate);
        }
    }
}
