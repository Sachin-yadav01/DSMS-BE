package com.dsms.supportrequests;

import com.dsms.common.api.Ids;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.AuditedEntity;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.EngagementService;
import com.dsms.engagements.SupportEngagement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Getter @Setter @Entity @Table(name = "support_request")
class SupportRequest extends AuditedEntity {
    @Column(nullable = false) private String displayCode;
    @Column(nullable = false) private Long engagementId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String description;
    @Column(nullable = false) private String priority;
    @Column(nullable = false) private String status;
    private Long assigneeUserId;
    private LocalDate targetDate;
    @Column(nullable = false) private boolean outsideMeetingWorkEnabled;
    private String outcomeSummary;
}

@Getter @Setter @Entity @Table(name = "support_request_comment")
class SupportRequestComment {
    @jakarta.persistence.Id @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long requestId;
    @Column(nullable = false) private String visibility;
    @Column(nullable = false) private String body;
    @Column(nullable = false) private Long authorUserId;
    @Column(nullable = false) private java.time.Instant createdOn;
}

@Repository
interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    List<SupportRequest> findByEngagementIdOrderByCreatedOnDesc(Long engagementId);
}

@Repository
interface SupportRequestCommentRepository extends JpaRepository<SupportRequestComment, Long> {
    List<SupportRequestComment> findByRequestIdOrderByCreatedOnAsc(Long requestId);
}

record RequestDto(String id, String displayCode, String engagementId, String title, String description, String priority, String status, boolean outsideMeetingWorkEnabled) {}
record RequestCreate(String engagementId, String title, String description, String priority) {}
record CommentDto(String id, String visibility, String body, String authorUserId, String createdOn) {}
record CommentCreate(String body, String visibility) {}

@Service
class SupportRequestService {
    private final SupportRequestRepository requests;
    private final SupportRequestCommentRepository comments;
    private final EngagementService engagements;
    private final CurrentUser currentUser;
    private final DisplayCodeService codes;
    private final java.time.Clock clock;

    SupportRequestService(SupportRequestRepository requests, SupportRequestCommentRepository comments, EngagementService engagements,
                          CurrentUser currentUser, DisplayCodeService codes, java.time.Clock clock) {
        this.requests = requests;
        this.comments = comments;
        this.engagements = engagements;
        this.currentUser = currentUser;
        this.codes = codes;
        this.clock = clock;
    }

    @Transactional
    public RequestDto create(RequestCreate request) {
        SupportEngagement engagement = engagements.requireVisible(Ids.parse(request.engagementId()));
        SupportRequest entity = new SupportRequest();
        entity.setDisplayCode(codes.next("REQ"));
        entity.setEngagementId(engagement.getId());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setPriority(request.priority() == null ? "MEDIUM" : request.priority());
        entity.setStatus("OPEN");
        requests.save(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<RequestDto> list(String engagementId) {
        Long id = Ids.parse(engagementId);
        engagements.requireVisible(id);
        return requests.findByEngagementIdOrderByCreatedOnDesc(id).stream().map(this::toDto).toList();
    }

    @Transactional
    public CommentDto comment(String id, CommentCreate request) {
        SupportRequest entity = requests.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Request not found"));
        engagements.requireVisible(entity.getEngagementId());
        String visibility = request.visibility() == null ? "SHARED" : request.visibility();
        if ("INTERNAL".equals(visibility) && !currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Internal comments are staff-only");
        }
        SupportRequestComment comment = new SupportRequestComment();
        comment.setRequestId(entity.getId());
        comment.setVisibility(visibility);
        comment.setBody(request.body());
        comment.setAuthorUserId(currentUser.requireUserId());
        comment.setCreatedOn(clock.instant());
        comments.save(comment);
        return toComment(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> comments(String id) {
        SupportRequest entity = requests.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Request not found"));
        engagements.requireVisible(entity.getEngagementId());
        boolean staff = currentUser.isAdmin() || currentUser.isCoordinator();
        return comments.findByRequestIdOrderByCreatedOnAsc(entity.getId()).stream()
                .filter(c -> staff || "SHARED".equals(c.getVisibility()))
                .map(this::toComment)
                .toList();
    }

    @Transactional
    public RequestDto updateStatus(String id, String status, String outcome) {
        SupportRequest entity = requests.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Request not found"));
        engagements.requireVisible(entity.getEngagementId());
        entity.setStatus(status);
        entity.setOutcomeSummary(outcome);
        return toDto(entity);
    }

    private RequestDto toDto(SupportRequest entity) {
        return new RequestDto(Ids.toJson(entity.getId()), entity.getDisplayCode(), Ids.toJson(entity.getEngagementId()),
                entity.getTitle(), entity.getDescription(), entity.getPriority(), entity.getStatus(), entity.isOutsideMeetingWorkEnabled());
    }

    private CommentDto toComment(SupportRequestComment comment) {
        return new CommentDto(Ids.toJson(comment.getId()), comment.getVisibility(), comment.getBody(),
                Ids.toJson(comment.getAuthorUserId()), comment.getCreatedOn().toString());
    }
}

@RestController
@RequestMapping("/api/v1/support-requests")
class SupportRequestController {
    private final SupportRequestService requests;
    SupportRequestController(SupportRequestService requests) { this.requests = requests; }

    @GetMapping
    public List<RequestDto> list(@org.springframework.web.bind.annotation.RequestParam String engagementId) {
        return requests.list(engagementId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto create(@RequestBody RequestCreate request) { return requests.create(request); }

    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@PathVariable String id) { return requests.comments(id); }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto comment(@PathVariable String id, @RequestBody CommentCreate request) { return requests.comment(id, request); }

    @PostMapping("/{id}/status")
    public RequestDto status(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        return requests.updateStatus(id, body.get("status"), body.get("outcome"));
    }
}
