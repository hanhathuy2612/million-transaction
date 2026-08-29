package com.hnh.example.transaction_example.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.hnh.example.transaction_example.domain.Webhook;
import com.hnh.example.transaction_example.dto.CreateWebhookRequest;
import com.hnh.example.transaction_example.dto.WebhookResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WebhookMapper {
    Webhook toEntity(CreateWebhookRequest request);

    WebhookResponse toResponse(Webhook entity);
}
