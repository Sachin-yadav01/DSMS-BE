package com.dsms.billing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCandidateIdOrderByCreatedOnDesc(Long candidateId);
}
