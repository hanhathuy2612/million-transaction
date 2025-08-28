package com.hnh.example.transaction_example.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse implements Serializable {
    private Long id;
    private String merchantId;
    private String webhookUrl;
    private String webhookSecret;
    private String events;
}
