package com.dsms.candidates;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<SkillEntity, Long> {
    boolean existsByNormalizedName(String normalizedName);
    List<SkillEntity> findByStatusAndNameContainingIgnoreCase(String status, String name);
    List<SkillEntity> findByStatus(String status);
}
