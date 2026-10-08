package com.dsms.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);
}
