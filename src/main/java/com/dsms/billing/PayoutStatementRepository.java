package com.dsms.billing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutStatementRepository extends JpaRepository<PayoutStatement, Long> {
    List<PayoutStatement> findByExpertIdOrderByCreatedOnDesc(Long expertId);
}
