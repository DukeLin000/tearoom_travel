package com.tripmate.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Identity persistence; schema changes belong to Flyway, never Hibernate DDL. */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id private UUID id;
    @Column(name = "oidc_issuer", nullable = false, length = 255) private String issuer;
    @Column(name = "oidc_subject", nullable = false, length = 255) private String subject;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected AppUser() {}

    public User toUser() { return new User(id, displayName); }
}
