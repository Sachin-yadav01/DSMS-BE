package com.dsms.timetracking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkEntryRepository extends JpaRepository<WorkEntry, Long> {
    Optional<WorkEntry> findByOpenExpertId(Long expertId);
    List<WorkEntry> findByExpertIdOrderByCreatedOnDesc(Long expertId);
    List<WorkEntry> findByCandidateIdOrderByCreatedOnDesc(Long candidateId);
    List<WorkEntry> findByEngagementIdOrderByCreatedOnDesc(Long engagementId);
}
