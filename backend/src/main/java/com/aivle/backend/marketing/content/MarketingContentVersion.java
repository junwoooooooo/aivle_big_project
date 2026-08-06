package com.aivle.backend.marketing.content;

import com.aivle.backend.user.entity.User;
import com.aivle.backend.job.entity.AnalysisJob;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.aivle.backend.marketing.content.MarketingContentTypes.*;

@Entity
@Table(
    name = "marketing_content_versions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_marketing_content_version",
        columnNames = {"marketing_content_id", "version_number"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingContentVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketing_content_id", nullable = false)
    private MarketingContent marketingContent;
    @Column(nullable = false) private int versionNumber;
    @Column(nullable = false, length = 160) private String headline;
    @Column(length = 240) private String subheadline;
    @Column(columnDefinition = "TEXT") private String bodyCopy;
    @Column(length = 80) private String callToAction;
    @Column(length = 240) private String supportingText;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Tone visualStyle;
    @Column(nullable = false, length = 30) private String colorTheme;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Template layoutTemplate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private BackgroundType backgroundType;
    @Column(length = 500) private String backgroundValue;
    @Column(length = 20) private String accentColor;
    @Column(length = 20) private String textColor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TextAlignment textAlignment;
    @Column(nullable = false) private int headlineSize;
    @Column(nullable = false) private boolean showCta;
    @Column(nullable = false) private boolean showPersonaTag;
    @Column(nullable = false, columnDefinition = "TEXT") private String contentJson;
    @Column(nullable = false) private int sourceSnapshotVersion;
    @Column(nullable = false) private boolean sourceChanged;
    @Column(nullable = false) private boolean copyChanged;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id")
    private AnalysisJob analysisJob;

    public static MarketingContentVersion create(
        MarketingContent content,
        int versionNumber,
        User actor,
        Draft draft,
        LocalDateTime now,
        int sourceSnapshotVersion,
        boolean sourceChanged,
        boolean copyChanged
    ) {
        MarketingContentVersion value = new MarketingContentVersion();
        value.marketingContent = content;
        value.versionNumber = versionNumber;
        value.createdBy = actor;
        value.createdAt = now;
        value.sourceSnapshotVersion = sourceSnapshotVersion;
        value.sourceChanged = sourceChanged;
        value.copyChanged = copyChanged;
        value.apply(draft, now);
        return value;
    }

    public static MarketingContentVersion generated(
        MarketingContent content,
        int versionNumber,
        User actor,
        Draft draft,
        LocalDateTime now,
        int sourceSnapshotVersion,
        AnalysisJob analysisJob
    ) {
        MarketingContentVersion value = create(
            content,
            versionNumber,
            actor,
            draft,
            now,
            sourceSnapshotVersion,
            false,
            false
        );
        value.analysisJob = analysisJob;
        return value;
    }

    public Draft toDraft() {
        return new Draft(
            headline, subheadline, bodyCopy, callToAction,
            supportingText, visualStyle, colorTheme, layoutTemplate,
            backgroundType, backgroundValue, accentColor, textColor,
            textAlignment, headlineSize, showCta, showPersonaTag,
            contentJson
        );
    }

    public void apply(Draft draft, LocalDateTime now) {
        this.headline = draft.headline();
        this.subheadline = draft.subheadline();
        this.bodyCopy = draft.bodyCopy();
        this.callToAction = draft.callToAction();
        this.supportingText = draft.supportingText();
        this.visualStyle = draft.visualStyle();
        this.colorTheme = draft.colorTheme();
        this.layoutTemplate = draft.layoutTemplate();
        this.backgroundType = draft.backgroundType();
        this.backgroundValue = draft.backgroundValue();
        this.accentColor = draft.accentColor();
        this.textColor = draft.textColor();
        this.textAlignment = draft.textAlignment();
        this.headlineSize = draft.headlineSize();
        this.showCta = draft.showCta();
        this.showPersonaTag = draft.showPersonaTag();
        this.contentJson = draft.contentJson();
        this.updatedAt = now;
    }

    public void markSourceChanged(int snapshotVersion, LocalDateTime now) {
        this.sourceSnapshotVersion = snapshotVersion;
        this.sourceChanged = true;
        this.copyChanged = false;
        this.updatedAt = now;
    }

    public record Draft(
        String headline,
        String subheadline,
        String bodyCopy,
        String callToAction,
        String supportingText,
        Tone visualStyle,
        String colorTheme,
        Template layoutTemplate,
        BackgroundType backgroundType,
        String backgroundValue,
        String accentColor,
        String textColor,
        TextAlignment textAlignment,
        int headlineSize,
        boolean showCta,
        boolean showPersonaTag,
        String contentJson
    ) { }
}
