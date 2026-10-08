package com.dsms.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthenticationAttemptRepository extends JpaRepository<AuthenticationAttempt, Long> {
    @Query("select count(a) from AuthenticationAttempt a where a.emailNormalized = :email and a.success = false and a.attemptedOn >= :since")
    long countRecentFailures(@Param("email") String email, @Param("since") Instant since);
}
