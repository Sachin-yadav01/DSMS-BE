package com.dsms.notifications;

import com.dsms.common.api.Ids;
import com.dsms.common.security.CurrentUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Getter
@Setter
@Entity
@Table(name = "notification")
class AppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String body;
    private String linkPath;
    private Instant readOn;
    @Column(nullable = false)
    private Instant createdOn;
}

@Repository
interface NotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findByUserIdOrderByCreatedOnDesc(Long userId, PageRequest pageable);
    long countByUserIdAndReadOnIsNull(Long userId);
}

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final CurrentUser currentUser;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, CurrentUser currentUser, Clock clock) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public void notifyUser(Long userId, String title, String body, String linkPath) {
        AppNotification notification = new AppNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLinkPath(linkPath);
        notification.setCreatedOn(clock.instant());
        notifications.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listMine() {
        return notifications.findByUserIdOrderByCreatedOnDesc(currentUser.requireUserId(), PageRequest.of(0, 50))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByUserIdAndReadOnIsNull(currentUser.requireUserId());
    }

    @Transactional
    public void markAllRead() {
        Instant now = clock.instant();
        notifications.findByUserIdOrderByCreatedOnDesc(currentUser.requireUserId(), PageRequest.of(0, 200))
                .forEach(n -> {
                    if (n.getReadOn() == null) {
                        n.setReadOn(now);
                    }
                });
    }

    private NotificationDto toDto(AppNotification notification) {
        return new NotificationDto(Ids.toJson(notification.getId()), notification.getTitle(), notification.getBody(),
                notification.getLinkPath(), notification.getReadOn() != null, notification.getCreatedOn().toString());
    }
}

record NotificationDto(String id, String title, String body, String linkPath, boolean read, String createdOn) {}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
    private final NotificationService notifications;
    NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public List<NotificationDto> list() { return notifications.listMine(); }

    @GetMapping("/unread-count")
    public UnreadCount unread() { return new UnreadCount(notifications.unreadCount()); }

    @PostMapping("/read-all")
    public void readAll() { notifications.markAllRead(); }

    record UnreadCount(long count) {}
}
