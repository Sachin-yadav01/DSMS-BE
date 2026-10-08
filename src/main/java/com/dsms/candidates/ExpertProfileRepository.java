package com.dsms.candidates;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Long> {
    Optional<ExpertProfile> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExpertProfile e where e.id = :id")
    Optional<ExpertProfile> lockById(@Param("id") Long id);
}
