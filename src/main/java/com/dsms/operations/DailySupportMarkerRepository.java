package com.dsms.operations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySupportMarkerRepository extends JpaRepository<DailySupportMarker, Long> {
    Optional<DailySupportMarker> findByCandidateIdAndSupportDate(Long candidateId, LocalDate supportDate);
    List<DailySupportMarker> findBySupportDate(LocalDate supportDate);
}
