package com.tripmate.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final AppUserRepository users;
    public IdentityService(AppUserRepository users) { this.users = users; }
    @Transactional
    public User devUser(String subject, String displayName) {
        users.upsertDevelopmentUser(UUID.randomUUID(), subject, displayName);
        return users.findByIssuerAndSubject("urn:tripmate:local-dev", subject)
            .orElseThrow(() -> new IllegalStateException("Identity upsert did not persist a user")).toUser();
    }
}
