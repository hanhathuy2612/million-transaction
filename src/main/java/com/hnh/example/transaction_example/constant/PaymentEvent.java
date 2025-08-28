package com.hnh.example.transaction_example.constant;

public final class PaymentEvent {
    public static final String EVENT_TYPE_AUTHORIZED = "payment.authorized";
    public static final String EVENT_TYPE_CAPTURED = "payment.captured";
    public static final String EVENT_TYPE_REFUNDED = "payment.refunded";
    public static final String EVENT_TYPE_FAILED = "payment.failed";
    public static final String EVENT_TYPE_PENDING = "payment.pending";
}
