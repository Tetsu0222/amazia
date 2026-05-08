package com.example.user;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.notification.entity.NotificationSubscription;
import com.example.notification.repository.NotificationSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.example.shared.config.TestAwsConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired NotificationSubscriptionRepository subscriptionRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private User createUser(String employeeId, String email, boolean active) {
        Role role = roleRepository.findByCode("user").orElseThrow();
        User user = new User();
        user.setEmployeeId(employeeId);
        user.setEmail(email);
        user.setName("テスト" + employeeId);
        user.setPasswordHash(encoder.encode("Pass@1234"));
        user.setRole(role);
        user.setActiveFlag(active);
        return userRepository.save(user);
    }

    // --- GET /api/users ---

    @Test
    void 社員一覧が取得できること() throws Exception {
        createUser("EMP001", "emp1@example.com", true);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("emp1@example.com"));
    }

    // --- POST /api/users ---

    @Test
    void 社員登録が成功すると201が返ること() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"NEW001\",\"email\":\"new@example.com\",\"name\":\"新規社員\",\"password\":\"Pass@1234\",\"role\":\"user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void パスワードが8文字未満は422() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"E001\",\"email\":\"x@example.com\",\"name\":\"A\",\"password\":\"Ab1\",\"role\":\"user\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void パスワードに英大文字なしは422() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"E002\",\"email\":\"x@example.com\",\"name\":\"A\",\"password\":\"abcde123\",\"role\":\"user\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void パスワードに英小文字なしは422() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"E003\",\"email\":\"x@example.com\",\"name\":\"A\",\"password\":\"ABCDE123\",\"role\":\"user\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void パスワードに数字なしは422() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"E004\",\"email\":\"x@example.com\",\"name\":\"A\",\"password\":\"Abcdefgh\",\"role\":\"user\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void メールアドレス重複は422() throws Exception {
        createUser("EMP_DUP", "dup@example.com", true);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP_DUP2\",\"email\":\"dup@example.com\",\"name\":\"B\",\"password\":\"Pass@1234\",\"role\":\"user\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 社員ID重複は422() throws Exception {
        createUser("EMP_SAME", "unique1@example.com", true);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP_SAME\",\"email\":\"unique2@example.com\",\"name\":\"B\",\"password\":\"Pass@1234\",\"role\":\"user\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 必須項目が空は422() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void パスワードはBCryptハッシュで保存されること() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP_HASH\",\"email\":\"hash@example.com\",\"name\":\"H\",\"password\":\"Pass@1234\",\"role\":\"user\"}"))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("hash@example.com").orElseThrow();
        assertNotEquals("Pass@1234", saved.getPasswordHash());
        assertTrue(encoder.matches("Pass@1234", saved.getPasswordHash()));
    }

    // --- PUT /api/users/{id} ---

    @Test
    void 社員情報が更新できること() throws Exception {
        User user = createUser("EMP_UPD", "upd@example.com", true);

        mockMvc.perform(put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new_upd@example.com\",\"name\":\"更新後\",\"role\":\"admin\",\"activeFlag\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new_upd@example.com"))
                .andExpect(jsonPath("$.name").value("更新後"));
    }

    @Test
    void 存在しないIDの更新は404() throws Exception {
        mockMvc.perform(put("/api/users/9999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.com\",\"name\":\"X\",\"role\":\"user\",\"activeFlag\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activeFlagをfalseにするとログイン不可になること() throws Exception {
        User user = createUser("EMP_DEACT", "deact@example.com", true);

        mockMvc.perform(put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"deact@example.com\",\"name\":\"非有効\",\"role\":\"user\",\"activeFlag\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"deact@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isForbidden());
    }

    // --- フェーズ17 Step 6-4: notification_subscriptions 自動同期フック ---

    @Test
    void admin_として登録すると_全タグが自動購読される() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP_NS1\",\"email\":\"ns1@example.com\",\"name\":\"管理者A\",\"password\":\"Pass@1234\",\"role\":\"admin\"}"))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("ns1@example.com").orElseThrow();
        List<NotificationSubscription> subs = subscriptionRepository.findByUserId(saved.getId());
        assertEquals(5, subs.size());
        for (NotificationSubscription s : subs) {
            assertEquals(Boolean.TRUE, s.getEmailEnabled());
            assertEquals(Boolean.TRUE, s.getInAppEnabled());
        }
    }

    @Test
    void user_として登録すると_購読は作成されない() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP_NS2\",\"email\":\"ns2@example.com\",\"name\":\"一般\",\"password\":\"Pass@1234\",\"role\":\"user\"}"))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("ns2@example.com").orElseThrow();
        assertTrue(subscriptionRepository.findByUserId(saved.getId()).isEmpty());
    }

    @Test
    void user_から_admin_へ昇格すると_全タグが有効化される() throws Exception {
        User user = createUser("EMP_NS3", "ns3@example.com", true);

        mockMvc.perform(put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ns3@example.com\",\"name\":\"昇格後\",\"role\":\"admin\",\"activeFlag\":true}"))
                .andExpect(status().isOk());

        List<NotificationSubscription> subs = subscriptionRepository.findByUserId(user.getId());
        assertEquals(5, subs.size());
        for (NotificationSubscription s : subs) {
            assertEquals(Boolean.TRUE, s.getEmailEnabled());
        }
    }

    @Test
    void admin_から_user_へ降格すると_全行が_FALSE_化される() throws Exception {
        User user = createUser("EMP_NS4", "ns4@example.com", true);
        // まず admin に昇格して購読を作る
        mockMvc.perform(put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ns4@example.com\",\"name\":\"管理者\",\"role\":\"admin\",\"activeFlag\":true}"))
                .andExpect(status().isOk());

        // 続けて user へ降格
        mockMvc.perform(put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ns4@example.com\",\"name\":\"降格後\",\"role\":\"user\",\"activeFlag\":true}"))
                .andExpect(status().isOk());

        List<NotificationSubscription> subs = subscriptionRepository.findByUserId(user.getId());
        assertEquals(5, subs.size(), "降格でも行は物理削除しない");
        for (NotificationSubscription s : subs) {
            assertEquals(Boolean.FALSE, s.getEmailEnabled());
            assertEquals(Boolean.FALSE, s.getInAppEnabled());
        }
    }
}
