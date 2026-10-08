package com.dsms.engagements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpertAssignmentRepositoryBridge extends JpaRepository<ExpertAssignment, Long> {
}
