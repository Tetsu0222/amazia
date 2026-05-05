package com.example.user;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
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
}
