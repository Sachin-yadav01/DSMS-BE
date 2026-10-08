package com.dsms.supportrequests;

import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.security.CurrentUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Getter @Setter
@Entity
@Table(name = "attachment")
class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String storageKey;
    @Column(nullable = false) private String originalFilename;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private String parentType;
    @Column(nullable = false) private Long parentId;
    private java.time.Instant archivedOn;
    @Column(nullable = false) private Long uploadedBy;
    @Column(nullable = false) private java.time.Instant createdOn;
}

interface AttachmentRepository extends org.springframework.data.jpa.repository.JpaRepository<Attachment, Long> {}

@Service
class AttachmentService {
    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "application/pdf", "text/plain");
    private final AttachmentRepository attachments;
    private final DsmsProperties properties;
    private final CurrentUser currentUser;
    private final Clock clock;

    AttachmentService(AttachmentRepository attachments, DsmsProperties properties, CurrentUser currentUser, Clock clock) {
        this.attachments = attachments;
        this.properties = properties;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public AttachmentDto upload(String parentType, String parentId, MultipartFile file) throws Exception {
        if (file.getSize() > properties.getStorage().getMaxFileBytes()) {
            throw Exceptions.badRequest("FILE_TOO_LARGE", "File exceeds 10 MB");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED.contains(contentType)) {
            throw Exceptions.badRequest("FILE_TYPE", "Only PNG, JPEG, PDF, and TXT are allowed");
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        Path dir = Path.of(properties.getStorage().getDirectory());
        Files.createDirectories(dir);
        Path target = dir.resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        Attachment attachment = new Attachment();
        attachment.setStorageKey(key);
        attachment.setOriginalFilename(Path.of(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()).getFileName().toString());
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setParentType(parentType);
        attachment.setParentId(com.dsms.common.api.Ids.parse(parentId));
        attachment.setUploadedBy(currentUser.requireUserId());
        attachment.setCreatedOn(clock.instant());
        attachments.save(attachment);
        return new AttachmentDto(com.dsms.common.api.Ids.toJson(attachment.getId()), attachment.getOriginalFilename(), attachment.getContentType(), attachment.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FileSystemResource> download(String id) {
        Attachment attachment = attachments.findById(com.dsms.common.api.Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Attachment not found"));
        Path file = Path.of(properties.getStorage().getDirectory()).resolve(attachment.getStorageKey());
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(resource);
    }
}

record AttachmentDto(String id, String filename, String contentType, long sizeBytes) {}

@RestController
@RequestMapping("/api/v1/attachments")
class AttachmentController {
    private final AttachmentService attachments;
    AttachmentController(AttachmentService attachments) { this.attachments = attachments; }

    @PostMapping
    public AttachmentDto upload(@RequestParam String parentType, @RequestParam String parentId, @RequestParam MultipartFile file) throws Exception {
        return attachments.upload(parentType, parentId, file);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
        return attachments.download(id);
    }
}
