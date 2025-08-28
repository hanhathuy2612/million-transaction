package com.hnh.example.transaction_example.config.security;

import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component
public class JpaAuditListener implements AuditorAware<String> {

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityUtils.getCurrentUserLogin().orElse(AuthoritiesConstants.SYSTEM));
    }
}
