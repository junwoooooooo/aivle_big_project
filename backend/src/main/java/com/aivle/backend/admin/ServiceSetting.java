package com.aivle.backend.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceSetting {
    @Id
    private String settingKey;
    private String settingValue;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public ServiceSetting(String settingKey, String settingValue, Long updatedBy, LocalDateTime updatedAt) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void update(String value, Long actorId, LocalDateTime now) {
        this.settingValue = value;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
}
