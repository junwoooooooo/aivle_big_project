package com.aivle.backend.admin;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.persona.catalog.repository.ClusterPersonaPolicyRepository;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSettingService {
    private final ServiceSettingRepository settings;
    private final UserRepository users;
    private final AdminReauthenticationService reauthentication;
    private final AdminAuditService audits;
    private final Clock clock;
    private final ClusterPersonaPolicyRepository clusterPersonaPolicies;
    private final BaselinePersonaRepository baselinePersonas;

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        Map<String, ServiceSetting> stored = settings.findAll().stream()
            .collect(Collectors.toMap(ServiceSetting::getSettingKey, Function.identity()));
        List<Long> updaterIds = stored.values().stream()
            .map(ServiceSetting::getUpdatedBy)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, User> updaters = users.findAllById(updaterIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        return Arrays.stream(ServiceSettingKey.values())
            .map(key -> response(key, stored.get(key.name()), updaters))
            .toList();
    }

    @Transactional
    public SettingResponse update(
        User actor,
        String rawKey,
        String rawValue,
        String reason,
        String actionToken,
        AdminAuditContext context
    ) {
        ServiceSettingKey key;
        boolean nextValue;
        try {
            key = parseKey(rawKey);
            nextValue = parseBoolean(rawValue);
        } catch (BusinessException failure) {
            audits.recordFailureSafely(
                actor.getId(),
                AdminAuditAction.SERVICE_SETTING_CHANGED,
                AdminAuditTargetType.SERVICE_SETTING,
                null,
                rawKey == null || rawKey.isBlank() ? "UNKNOWN" : rawKey,
                reason,
                failure.getErrorCode().name(),
                context,
                Map.of()
            );
            throw failure;
        }
        ServiceSetting setting = settings.findById(key.name()).orElse(null);
        boolean currentValue = setting == null
            ? Boolean.parseBoolean(key.defaultValue())
            : Boolean.parseBoolean(setting.getSettingValue());

        if (currentValue == nextValue) {
            throw new BusinessException(ErrorCode.SERVICE_SETTING_ALREADY_APPLIED);
        }

        try {
            if (key == ServiceSettingKey.CLUSTER_PERSONA_ENABLED
                && nextValue) {
                baselinePersonas.lockActiveCatalog(BaselinePersonaCatalog.VERSION);
                if (clusterPersonaPolicies.countByEnabledTrue() == 0) {
                    throw new BusinessException(ErrorCode.CLUSTER_PERSONA_SELECTION_REQUIRED);
                }
            }
            if (key == ServiceSettingKey.MAINTENANCE_MODE && nextValue) {
                reauthentication.requireAndConsume(
                    actor,
                    actionToken,
                    AdminActionPurpose.MAINTENANCE_MODE_ENABLE,
                    context
                );
            }

            LocalDateTime now = LocalDateTime.now(clock);
            String storedValue = Boolean.toString(nextValue);
            if (setting == null) {
                setting = new ServiceSetting(key.name(), storedValue, actor.getId(), now);
            } else {
                setting.update(storedValue, actor.getId(), now);
            }
            settings.save(setting);
            audits.recordSuccess(
                actor.getId(), auditAction(key),
                AdminAuditTargetType.SERVICE_SETTING,
                null,
                key.name(),
                reason,
                Map.of("value", currentValue),
                Map.of("value", nextValue),
                context,
                Map.of("settingKey", key.name())
            );
            return response(key, setting, Map.of(actor.getId(), actor));
        } catch (BusinessException failure) {
            audits.recordFailureSafely(
                actor.getId(), auditAction(key),
                AdminAuditTargetType.SERVICE_SETTING,
                null,
                key.name(),
                reason,
                failure.getErrorCode().name(),
                context,
                Map.of("settingKey", key.name())
            );
            throw failure;
        }
    }

    private ServiceSettingKey parseKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_SETTING_INVALID);
        }
        try {
            return ServiceSettingKey.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SERVICE_SETTING_INVALID);
        }
    }

    private AdminAuditAction auditAction(ServiceSettingKey key) {
        return key == ServiceSettingKey.CLUSTER_PERSONA_ENABLED
            ? AdminAuditAction.CLUSTER_PERSONA_POLICY_CHANGED
            : AdminAuditAction.SERVICE_SETTING_CHANGED;
    }

    private boolean parseBoolean(String value) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new BusinessException(ErrorCode.SERVICE_SETTING_INVALID);
        }
        return Boolean.parseBoolean(value);
    }

    private SettingResponse response(
        ServiceSettingKey key,
        ServiceSetting setting,
        Map<Long, User> updaters
    ) {
        boolean value = Boolean.parseBoolean(setting == null ? key.defaultValue() : setting.getSettingValue());
        UpdatedByResponse updatedBy = null;
        if (setting != null && setting.getUpdatedBy() != null) {
            User updater = updaters.get(setting.getUpdatedBy());
            updatedBy = updater == null
                ? new UpdatedByResponse(setting.getUpdatedBy(), null, null)
                : new UpdatedByResponse(updater.getId(), updater.getUsername(), updater.getName());
        }
        return new SettingResponse(
            key.name(),
            value,
            key.displayName(),
            key.description(),
            setting == null ? null : setting.getUpdatedAt(),
            updatedBy
        );
    }

    public record UpdatedByResponse(Long id, String username, String displayName) { }

    public record SettingResponse(
        String key,
        boolean value,
        String displayName,
        String description,
        LocalDateTime updatedAt,
        UpdatedByResponse updatedBy
    ) { }
}
