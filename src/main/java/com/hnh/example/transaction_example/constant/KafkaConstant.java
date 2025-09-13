package com.hnh.example.transaction_example.constant;

public final class KafkaConstant {
    public static final String PAYMENT_EVENTS_TOPIC = "payments.events.v1";
    public static final String PAYMENT_EVENTS_DLT_TOPIC = "payments.events.v1.dlt"; // Dead Letter Topic
    public static final String WEBHOOK_EVENTS_TOPIC = "webhooks.events.v1";
    public static final String PAYMENT_EVENTS_GROUP_ID = "webhook-processor";
    public static final String PAYMENT_EVENTS_GROUP_ID_READ_MODEL = "read-model-projector";
}
