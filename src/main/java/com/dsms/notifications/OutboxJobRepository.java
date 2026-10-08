package com.dsms.notifications;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxJobRepository extends JpaRepository<OutboxJob, Long> {
    boolean existsByDedupKey(String dedupKey);

    List<OutboxJob> findTop50ByStatusAndNextAttemptOnLessThanEqualOrderByNextAttemptOnAsc(String status, Instant now);
}
