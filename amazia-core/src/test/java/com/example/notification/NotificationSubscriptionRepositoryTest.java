package com.example.notification;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.NotificationSubscriptionRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: notification_subscriptions Entity / Repository の永続化検証。
 * (user_id, subscription_tag) UNIQUE と FK 整合を確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class NotificationSubscriptionRepositoryTest {

    @Autowired
    private NotificationSubscriptionRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_すると_id_と_既定値_TRUE_が反映される() {
        Long userId = createUser("ns-default");

        NotificationSubscription saved = repository.saveAndFlush(newSub(userId, "inventory_alerts"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(Boolean.TRUE, saved.getEmailEnabled());
        assertEquals(Boolean.TRUE, saved.getInAppEnabled());
    }

    @Test
    void 同じ_user_id_と_subscription_tag_は_UNIQUE_違反になる() {
        Long userId = createUser("ns-unique");
        repository.saveAndFlush(newSub(userId, "sales_alerts"));

        NotificationSubscription dup = newSub(userId, "sales_alerts");
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(dup));
    }

    @Test
    void findBySubscriptionTagAndEmailEnabledTrue_で配信先を解決できる() {
        Long userA = createUser("ns-a");
        Long userB = createUser("ns-b");
        repository.saveAndFlush(newSub(userA, "delivery_alerts"));
        NotificationSubscription disabled = newSub(userB, "delivery_alerts");
        disabled.setEmailEnabled(false);
        repository.saveAndFlush(disabled);

        List<NotificationSubscription> result =
                repository.findBySubscriptionTagAndEmailEnabledTrue("delivery_alerts");

        assertEquals(1, result.size());
        assertEquals(userA, result.get(0).getUserId());
    }

    @Test
    void findByUserIdAndSubscriptionTag_で取得できる() {
        Long userId = createUser("ns-find");
        repository.saveAndFlush(newSub(userId, "batch_failure"));

        Optional<NotificationSubscription> found =
                repository.findByUserIdAndSubscriptionTag(userId, "batch_failure");

        assertTrue(found.isPresent());
    }

    private Long createUser(String prefix) {
        User u = new User();
        u.setEmployeeId(prefix + (System.nanoTime() % 100000));
        u.setEmail(prefix + "-" + System.nanoTime() + "@example.com");
        u.setName(prefix);
        u.setPasswordHash("dummy");
        u.setActiveFlag(true);
        return userRepository.save(u).getId();
    }

    private NotificationSubscription newSub(Long userId, String tag) {
        NotificationSubscription s = new NotificationSubscription();
        s.setUserId(userId);
        s.setSubscriptionTag(tag);
        return s;
    }
}
