package com.dsms.audit;

import com.dsms.common.api.Ids;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.security.CurrentUser;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

record AuditEventDto(String id, String action, String resourceType, String resourceId, String actorUserId,
                     String beforeJson, String afterJson, String createdOn) {}

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    private final AuditEventRepository events;
    private final CurrentUser currentUser;

    public AuditController(AuditEventRepository events, CurrentUser currentUser) {
        this.events = events;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AuditEventDto> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size,
                                    @RequestParam(required = false) String resourceType,
                                    @RequestParam(required = false) String resourceId) {
        if (!currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admin access required");
        }
        if (resourceType != null && !resourceType.isBlank() && resourceId != null && !resourceId.isBlank()) {
            return events.findByResourceTypeAndResourceId(resourceType, resourceId, PageRequest.of(page, Math.min(size, 100)))
                    .stream().map(this::toDto).toList();
        }
        return events.findAll(PageRequest.of(page, Math.min(size, 100))).stream().map(this::toDto).toList();
    }

    private AuditEventDto toDto(AuditEvent event) {
        return new AuditEventDto(Ids.toJson(event.getId()), event.getAction(), event.getResourceType(), event.getResourceId(),
                event.getActorUserId() == null ? null : Ids.toJson(event.getActorUserId()),
                event.getBeforeJson(), event.getAfterJson(), event.getCreatedOn().toString());
    }
}
