package com.dsms.engagements;

import com.dsms.common.exception.Exceptions;
import org.springframework.stereotype.Service;

@Service
public class ExpertAssignmentLookup {
    private final ExpertAssignmentRepository assignments;

    public ExpertAssignmentLookup(ExpertAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    public ExpertAssignment get(Long id) {
        return assignments.findById(id).orElseThrow(() -> Exceptions.notFound("Assignment not found"));
    }
}
