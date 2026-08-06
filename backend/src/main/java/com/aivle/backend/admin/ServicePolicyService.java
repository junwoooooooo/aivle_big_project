package com.aivle.backend.admin;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicePolicyService {
    private final ServiceSettingRepository settings;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() { return enabled(ServiceSettingKey.REGISTRATION_ENABLED); }

    @Transactional(readOnly = true)
    public boolean isDocumentProcessingEnabled() { return enabled(ServiceSettingKey.DOCUMENT_PROCESSING_ENABLED); }

    @Transactional(readOnly = true)
    public boolean isMaintenanceMode() { return enabled(ServiceSettingKey.MAINTENANCE_MODE); }
    public boolean isClusterPersonaEnabled() {
        return enabled(ServiceSettingKey.CLUSTER_PERSONA_ENABLED);
    }

    @Transactional(readOnly = true)
    public ServicePolicySnapshot snapshot() {
        Map<String, ServiceSetting> valuesByKey = settings.findAllById(
            Arrays.stream(ServiceSettingKey.values()).map(Enum::name).toList()
        ).stream()
            .collect(Collectors.toMap(ServiceSetting::getSettingKey, Function.identity()));
        return new ServicePolicySnapshot(
            enabled(ServiceSettingKey.REGISTRATION_ENABLED, valuesByKey),
            enabled(ServiceSettingKey.DOCUMENT_PROCESSING_ENABLED, valuesByKey),
            enabled(ServiceSettingKey.MAINTENANCE_MODE, valuesByKey),
            enabled(ServiceSettingKey.CLUSTER_PERSONA_ENABLED, valuesByKey)
        );
    }

    public void requireRegistrationEnabled() { if (!isRegistrationEnabled()) throw new BusinessException(ErrorCode.REGISTRATION_DISABLED); }
    public void requireDocumentProcessingEnabled() { if (!isDocumentProcessingEnabled()) throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING_DISABLED); }
    public void requireServiceAvailableForUser() { if (isMaintenanceMode()) throw new BusinessException(ErrorCode.MAINTENANCE_MODE_ENABLED); }

    @Transactional(readOnly = true)
    public void requireWriteAvailableForUser(Long userId) {
        if (!enabled(ServiceSettingKey.MAINTENANCE_MODE)) {
            return;
        }
        User user = users.findByIdAndDeletedAtIsNull(userId)
            .filter(User::canLogin)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.MAINTENANCE_MODE_ENABLED);
        }
    }

    private boolean enabled(ServiceSettingKey key) {
        String value = settings.findById(key.name()).map(ServiceSetting::getSettingValue)
            .orElse(key.defaultValue());
        return Boolean.parseBoolean(value);
    }

    private boolean enabled(ServiceSettingKey key, Map<String, ServiceSetting> valuesByKey) {
        ServiceSetting setting = valuesByKey.get(key.name());
        return Boolean.parseBoolean(setting == null ? key.defaultValue() : setting.getSettingValue());
    }

    public record ServicePolicySnapshot(
        boolean registrationEnabled,
        boolean documentProcessingEnabled,
        boolean maintenanceMode,
        boolean clusterPersonaEnabled
    ) { }
}
