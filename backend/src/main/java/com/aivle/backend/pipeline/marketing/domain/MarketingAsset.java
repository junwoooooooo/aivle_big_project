package com.aivle.backend.pipeline.marketing.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "pipeline_marketing_assets")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingAsset extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "content_id", nullable = false, length = 64) private String contentId;
    @Column(name = "revision_id", nullable = false, length = 64) private String revisionId;
    @Column(name = "artifact_ref", nullable = false, length = 300) private String artifactRef;
    public static MarketingAsset link(String contentId, String revisionId, String ref) {
        MarketingAsset value = new MarketingAsset(); value.id = UUID.randomUUID().toString();
        value.contentId = contentId; value.revisionId = revisionId; value.artifactRef = ref; return value;
    }
}
