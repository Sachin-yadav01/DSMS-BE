package com.dsms.users;

import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailNormalized(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    Page<UserAccount> findByRole(UserRole role, Pageable pageable);

    Page<UserAccount> findByStatus(UserStatus status, Pageable pageable);
}
