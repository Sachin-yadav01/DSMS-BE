package com.dsms.notifications;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final OutboxJobRepository jobs;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxJobRepository jobs, ObjectMapper objectMapper, Clock clock) {
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(String jobType, String dedupKey, Map<String, String> payload) {
        if (jobs.existsByDedupKey(dedupKey)) {
            return;
        }
        OutboxJob job = new OutboxJob();
        job.setJobType(jobType);
        job.setDedupKey(dedupKey);
        job.setStatus("PENDING");
        job.setAttempts(0);
        Instant now = clock.instant();
        job.setNextAttemptOn(now);
        job.setCreatedOn(now);
        try {
            job.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JacksonException ex) {
            throw new IllegalStateException(ex);
        }
        jobs.save(job);
    }
}
