package com.tripmate.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByIssuerAndSubject(String issuer, String subject);

    // PostgreSQL upsert also handles concurrent first logins without duplicate identities.
    @Modifying
    @Query(value = """
        INSERT INTO app_user(id, oidc_issuer, oidc_subject, display_name)
        VALUES (:id, 'urn:tripmate:local-dev', :subject, :displayName)
        ON CONFLICT (oidc_issuer, oidc_subject) DO UPDATE
        SET display_name=excluded.display_name, version=app_user.version+1
        """, nativeQuery = true)
    void upsertDevelopmentUser(@Param("id") UUID id, @Param("subject") String subject,
        @Param("displayName") String displayName);
}
