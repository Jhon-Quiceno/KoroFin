package com.korofin.backend.notification.mapper;

import com.korofin.backend.notification.dto.NotificationPreferenceRequest;
import com.korofin.backend.notification.dto.NotificationPreferenceResponse;
import com.korofin.backend.notification.entity.NotificationPreference;
import com.korofin.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferenceMapperTest {

    private final NotificationPreferenceMapper mapper = Mappers.getMapper(NotificationPreferenceMapper.class);

    @Test
    void toResponseMapsEveryToggle() {
        NotificationPreference preference = new NotificationPreference();
        preference.setPaymentReminders(true);
        preference.setOverspendAlerts(false);
        preference.setWeeklySummary(true);
        preference.setInactivityReminders(false);
        preference.setCardCycleClose(true);
        preference.setEmailEnabled(true);

        NotificationPreferenceResponse response = mapper.toResponse(preference);

        assertThat(response).isEqualTo(new NotificationPreferenceResponse(true, false, true, false, true, true));
    }

    @Test
    void updateEntityFromRequestIgnoresIdUserAndTimestamps() {
        User owner = new User();
        owner.setId(5L);

        NotificationPreference preference = new NotificationPreference();
        preference.setId(9L);
        preference.setUser(owner);
        preference.setPaymentReminders(false);

        NotificationPreferenceRequest request = new NotificationPreferenceRequest(true, true, true, true, true, true);
        mapper.updateEntityFromRequest(request, preference);

        assertThat(preference.getId()).isEqualTo(9L);
        assertThat(preference.getUser()).isSameAs(owner);
        assertThat(preference.isPaymentReminders()).isTrue();
        assertThat(preference.isEmailEnabled()).isTrue();
    }
}
