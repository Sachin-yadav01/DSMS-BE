package com.dsms.common.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DisplayCodeSequenceRepository extends JpaRepository<DisplayCodeSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DisplayCodeSequence> findByPrefix(String prefix);
}
