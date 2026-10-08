package com.dsms.engagements;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngagementTermsRepository extends JpaRepository<EngagementTerms, Long> {
    List<EngagementTerms> findByEngagementIdOrderByEffectiveFromDesc(Long engagementId);
}
