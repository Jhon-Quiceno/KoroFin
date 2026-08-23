package com.korofin.backend.controller.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korofin.backend.config.JwtProperties;
import com.korofin.backend.config.SecurityConfig;
import com.korofin.backend.dto.notification.NotificationPreferenceRequest;
import com.korofin.backend.dto.notification.NotificationPreferenceResponse;
import com.korofin.backend.dto.notification.NotificationResponse;
import com.korofin.backend.dto.notification.PushTokenRequest;
import com.korofin.backend.entity.notification.NotificationType;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.JwtService;
import com.korofin.backend.service.notification.NotificationService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private final NotificationResponse sampleNotification = new NotificationResponse(
            1L, NotificationType.PAYMENT_REMINDER, "Recordatorio", "Tu servicio vence", false, null, null
    );

    @BeforeEach
    void setUp() {
        Claims mockClaims = org.mockito.Mockito.mock(Claims.class);
        when(mockClaims.getSubject()).thenReturn("1");
        when(jwtService.parseAccessToken(any())).thenReturn(mockClaims);
        when(userRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void getNotificationsReturns200WithPagedResults() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationService.getNotifications(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleNotification), pageable, 1));

        mockMvc.perform(get("/api/notifications").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Recordatorio"));
    }

    @Test
    void getUnreadCountReturns200WithCount() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));
    }

    @Test
    void markAsReadReturns200WithUpdatedNotification() throws Exception {
        when(notificationService.markAsRead(1L)).thenReturn(
                new NotificationResponse(1L, NotificationType.PAYMENT_REMINDER, "Recordatorio", "Tu servicio vence", true, null, null)
        );

        mockMvc.perform(patch("/api/notifications/1/read").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markAllAsReadReturns204() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void getPreferencesReturns200() throws Exception {
        when(notificationService.getPreferences())
                .thenReturn(new NotificationPreferenceResponse(true, true, true, true, true, false));

        mockMvc.perform(get("/api/notifications/preferences").header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReminders").value(true));
    }

    @Test
    void updatePreferencesReturns200() throws Exception {
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(true, true, true, true, true, true);
        when(notificationService.updatePreferences(any(NotificationPreferenceRequest.class)))
                .thenReturn(new NotificationPreferenceResponse(true, true, true, true, true, true));

        mockMvc.perform(put("/api/notifications/preferences")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true));
    }

    @Test
    void updatePreferencesReturns400WhenAFieldIsMissing() throws Exception {
        String bodyMissingField = """
                {"paymentReminders": true, "overspendAlerts": true, "weeklySummary": true,
                 "inactivityReminders": true, "cardCycleClose": true}
                """;

        mockMvc.perform(put("/api/notifications/preferences")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingField))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerPushTokenReturns204() throws Exception {
        PushTokenRequest request = new PushTokenRequest("token", "device-1");

        mockMvc.perform(post("/api/notifications/push-token")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerPushTokenReturns400WhenDeviceIdIsBlank() throws Exception {
        PushTokenRequest request = new PushTokenRequest("token", "   ");

        mockMvc.perform(post("/api/notifications/push-token")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unregisterPushTokenReturns204() throws Exception {
        mockMvc.perform(delete("/api/notifications/push-token/device-1").header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void getNotificationsReturns403WithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());
    }
}
