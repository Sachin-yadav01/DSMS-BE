package com.dsms.common.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisplayCodeService {
    private final DisplayCodeSequenceRepository sequences;

    public DisplayCodeService(DisplayCodeSequenceRepository sequences) {
        this.sequences = sequences;
    }

    @Transactional
    public String next(String prefix) {
        DisplayCodeSequence sequence = sequences.findByPrefix(prefix)
                .orElseThrow(() -> new IllegalStateException("Missing display code sequence " + prefix));
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        return prefix + "-" + String.format("%06d", value);
    }
}
