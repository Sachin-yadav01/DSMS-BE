package com.dsms.audit;

import com.dsms.common.security.CurrentUser;
import com.dsms.common.web.TraceIdFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository events, CurrentUser currentUser, Clock clock, ObjectMapper objectMapper) {
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, String resourceId, Map<String, Object> before, Map<String, Object> after) {
        AuditEvent event = new AuditEvent();
        event.setActorUserId(currentUser.principal().map(p -> p.getId()).orElse(null));
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
        event.setBeforeJson(write(redact(before)));
        event.setAfterJson(write(redact(after)));
        event.setCreatedOn(clock.instant());
        events.save(event);
    }

    private Map<String, Object> redact(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        data.keySet().stream()
                .filter(k -> k.toLowerCase().contains("password") || k.toLowerCase().contains("token") || k.toLowerCase().contains("meeting"))
                .forEach(k -> data.put(k, "[redacted]"));
        return data;
    }

    private String write(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException ex) {
            return "{}";
        }
    }
}
