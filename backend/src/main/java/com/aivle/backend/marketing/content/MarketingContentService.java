package com.aivle.backend.marketing.content;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.marketing.content.MarketingContentVersion.Draft;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.catalog.repository.ProjectPersonaSelectionRepository;
import com.aivle.backend.persona.recommendation.repository.PersonaRecommendationRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static com.aivle.backend.marketing.content.MarketingContentTypes.*;

@Service
@RequiredArgsConstructor
public class MarketingContentService {
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final MarketingContentRepository contents;
    private final MarketingContentVersionRepository versions;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final BaselinePersonaRepository personas;
    private final ProjectPersonaSelectionRepository selections;
    private final PersonaRecommendationRepository recommendations;
    private final MarketingSourceSnapshotService sourceSnapshots;
    private final ServicePolicyService servicePolicy;
    private final DomainAuditService audits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SummaryResponse> list(Long userId, Long projectId) {
        User actor = activeUser(userId);
        ownedProject(projectId, actor);
        return contents.findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
            .stream()
            .map(this::summary)
            .toList();
    }

    @Transactional(readOnly = true)
    public DetailResponse detail(Long userId, Long projectId, Long contentId) {
        User actor = activeUser(userId);
        ownedProject(projectId, actor);
        MarketingContent content = content(projectId, contentId);
        return detail(content, currentVersion(content));
    }

    @Transactional
    public DetailResponse create(
        Long userId,
        Long projectId,
        CreateCommand command,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        Size size = validateSize(command.format(), command.width(), command.height());
        BaselinePersona persona = resolvePersona(project, command.personaId());
        String recommendedCode = recommendation(project).map(value ->
            value.getPrimaryPersonaCode()).orElse(null);
        MarketingSourceSnapshotService.SnapshotResult snapshot = sourceSnapshots.capture(
            project,
            persona,
            recommendedCode,
            sourceInput(command),
            command.panelInterviewId(),
            command.marketResponseId(),
            1
        );
        MarketingContent content = MarketingContent.create(
            project,
            actor,
            required(command.title(), 200),
            command.purpose(),
            command.channel(),
            command.format(),
            size.width(),
            size.height(),
            persona,
            recommendedCode,
            snapshot.json(),
            snapshot.panelInterviewId(),
            snapshot.marketResponseId(),
            snapshot.version()
        );
        contents.save(content);
        Draft draft = draft(
            project,
            persona,
            command,
            0,
            sourceSnapshots.guidance(snapshot.json())
        );
        MarketingContentVersion version = versions.save(
            MarketingContentVersion.create(
                content,
                1,
                actor,
                draft,
                LocalDateTime.now(clock),
                snapshot.version(),
                true,
                true
            )
        );
        audit(
            actor,
            project,
            AuditEventType.MARKETING_CONTENT_CREATED,
            content,
            requestId,
            Map.of("versionNumber", "1")
        );
        return detail(content, version);
    }

    @Transactional
    public DetailResponse update(
        Long userId,
        Long projectId,
        Long contentId,
        UpdateCommand command,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        MarketingContent content = content(projectId, contentId);
        if (command.entityVersion() != null
            && !command.entityVersion().equals(content.getVersion())) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_VERSION_CONFLICT);
        }
        Size size = validateSize(command.format(), command.width(), command.height());
        BaselinePersona persona = resolvePersona(project, command.personaId());
        content.updateMetadata(
            required(command.title(), 200),
            command.purpose(),
            command.channel(),
            command.format(),
            size.width(),
            size.height(),
            persona
        );
        MarketingContentVersion version = currentVersion(content);
        if (version.getAnalysisJob() != null) {
            int number = content.advanceVersion();
            version = versions.save(MarketingContentVersion.create(
                content,
                number,
                actor,
                validatedDraft(command.draft()),
                LocalDateTime.now(clock),
                content.getSourceSnapshotVersion(),
                false,
                true
            ));
        } else {
            version.apply(
                validatedDraft(command.draft()),
                LocalDateTime.now(clock));
        }
        audit(
            actor,
            project,
            AuditEventType.MARKETING_CONTENT_UPDATED,
            content,
            requestId,
            Map.of("versionNumber", Integer.toString(content.getCurrentVersion()))
        );
        return detail(content, version);
    }

    @Transactional
    public VersionResponse createVersion(
        Long userId,
        Long projectId,
        Long contentId,
        Draft draft,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        MarketingContent content = content(projectId, contentId);
        int number = content.advanceVersion();
        MarketingContentVersion version = versions.save(
            MarketingContentVersion.create(
                content,
                number,
                actor,
                validatedDraft(draft),
                LocalDateTime.now(clock),
                content.getSourceSnapshotVersion(),
                false,
                true
            )
        );
        audit(
            actor,
            project,
            AuditEventType.MARKETING_CONTENT_VERSION_CREATED,
            content,
            requestId,
            Map.of("versionNumber", Integer.toString(number))
        );
        return version(version);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(Long userId, Long projectId, Long contentId) {
        User actor = activeUser(userId);
        ownedProject(projectId, actor);
        content(projectId, contentId);
        return versions.findAllByMarketingContentIdOrderByVersionNumberDesc(contentId)
            .stream()
            .map(this::version)
            .toList();
    }

    @Transactional(readOnly = true)
    public DraftResponse alternateDraft(
        Long userId,
        Long projectId,
        Long contentId,
        int alternative
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        MarketingContent content = content(projectId, contentId);
        CreateCommand source = commandFrom(content, currentVersion(content));
        Draft draft = draft(
            project,
            content.getSelectedPersona(),
            source,
            alternative + 1,
            sourceSnapshots.guidance(content.getSourceSnapshotJson())
        );
        return new DraftResponse(
            draft,
            "VALIDATION_TEMPLATE",
            false,
            sourceSnapshots.legalNotice(content.getSourceSnapshotJson())
        );
    }

    @Transactional
    public DetailResponse refreshSource(
        Long userId,
        Long projectId,
        Long contentId,
        SourceRefreshCommand command,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        MarketingContent content = content(projectId, contentId);
        Map<String, Object> storedInput = sourceSnapshots.userInput(
            content.getSourceSnapshotJson()
        );
        MarketingSourceSnapshotService.SnapshotResult snapshot;
        try {
            snapshot = sourceSnapshots.capture(
                project,
                content.getSelectedPersona(),
                content.getRecommendedPersonaCode(),
                sourceInput(storedInput, currentVersion(content)),
                command.panelInterviewId(),
                command.marketResponseId(),
                content.getSourceSnapshotVersion() + 1
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.MARKETING_SOURCE_REFRESH_FAILED);
        }
        content.refreshSource(
            snapshot.json(),
            snapshot.panelInterviewId(),
            snapshot.marketResponseId()
        );
        if (command.generateDraft()) {
            MarketingContentVersion current = currentVersion(content);
            CreateCommand source = commandFrom(content, current);
            Draft replacement = draft(
                project,
                content.getSelectedPersona(),
                source,
                content.getCurrentVersion(),
                sourceSnapshots.guidance(snapshot.json())
            );
            int number = content.advanceVersion();
            versions.save(MarketingContentVersion.create(
                content,
                number,
                actor,
                replacement,
                LocalDateTime.now(clock),
                snapshot.version(),
                true,
                true
            ));
        } else {
            currentVersion(content).markSourceChanged(
                snapshot.version(),
                LocalDateTime.now(clock)
            );
        }
        audit(
            actor,
            project,
            AuditEventType.MARKETING_CONTENT_SOURCE_REFRESHED,
            content,
            requestId,
            sourceAuditMetadata(snapshot)
        );
        return detail(content, currentVersion(content));
    }

    @Transactional
    public void delete(
        Long userId,
        Long projectId,
        Long contentId,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        MarketingContent content = content(projectId, contentId);
        content.softDelete(LocalDateTime.now(clock));
        audit(
            actor,
            project,
            AuditEventType.MARKETING_CONTENT_DELETED,
            content,
            requestId,
            Map.of()
        );
    }

    private User activeUser(Long userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
            .filter(User::canLogin)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    private Project ownedProject(Long projectId, User actor) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, actor.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_ACCESS_DENIED));
    }

    private MarketingContent content(Long projectId, Long contentId) {
        return contents.findByIdAndProjectIdAndDeletedAtIsNull(contentId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));
    }

    private MarketingContentVersion currentVersion(MarketingContent content) {
        return versions.findByMarketingContentIdAndVersionNumber(
                content.getId(),
                content.getCurrentVersion()
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));
    }

    private BaselinePersona resolvePersona(Project project, Long requestedId) {
        Long personaId = requestedId;
        if (personaId == null) {
            personaId = selections.findByProjectId(project.getId())
                .map(value -> value.getPersona().getId())
                .orElse(null);
        }
        if (personaId != null) {
            return personas.findByIdAndCatalogVersionAndDeletedAtIsNull(
                    personaId,
                    BaselinePersonaCatalog.VERSION
                )
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.MARKETING_CONTENT_SOURCE_UNAVAILABLE
                ));
        }
        Optional<String> code = recommendation(project)
            .map(value -> value.getPrimaryPersonaCode());
        return code.flatMap(value ->
            personas.findByPersonaCodeAndCatalogVersionAndDeletedAtIsNull(
                value,
                BaselinePersonaCatalog.VERSION
            )).orElse(null);
    }

    private Optional<com.aivle.backend.persona.recommendation.entity.PersonaRecommendation>
    recommendation(Project project) {
        return recommendations
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(),
                project.getOwner().getId()
            );
    }

    private Size validateSize(Format format, Integer width, Integer height) {
        if (format == null) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_INVALID_FORMAT);
        }
        int actualWidth = format == Format.CUSTOM
            ? Optional.ofNullable(width).orElse(0)
            : format.width();
        int actualHeight = format == Format.CUSTOM
            ? Optional.ofNullable(height).orElse(0)
            : format.height();
        if (actualWidth < 320 || actualWidth > 4096
            || actualHeight < 320 || actualHeight > 4096) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_INVALID_SIZE);
        }
        return new Size(actualWidth, actualHeight);
    }

    private Draft validatedDraft(Draft draft) {
        if (draft == null
            || draft.visualStyle() == null
            || draft.layoutTemplate() == null
            || draft.backgroundType() == null
            || draft.textAlignment() == null
            || draft.headlineSize() < 28
            || draft.headlineSize() > 180) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_INVALID_FORMAT);
        }
        String headline = required(draft.headline(), 160);
        validateColor(draft.accentColor());
        validateColor(draft.textColor());
        if (draft.backgroundType() == BackgroundType.SOLID) {
            validateColor(draft.backgroundValue());
        }
        return new Draft(
            headline,
            trim(draft.subheadline(), 240),
            trim(draft.bodyCopy(), 2000),
            trim(draft.callToAction(), 80),
            trim(draft.supportingText(), 240),
            draft.visualStyle(),
            trim(draft.colorTheme(), 30),
            draft.layoutTemplate(),
            draft.backgroundType(),
            trim(draft.backgroundValue(), 500),
            draft.accentColor(),
            draft.textColor(),
            draft.textAlignment(),
            draft.headlineSize(),
            draft.showCta(),
            draft.showPersonaTag(),
            trim(draft.contentJson(), 4000)
        );
    }

    private void validateColor(String value) {
        if (value == null || !COLOR.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_INVALID_FORMAT);
        }
    }

    private Draft draft(
        Project project,
        BaselinePersona persona,
        CreateCommand command,
        int alternative,
        MarketingSourceSnapshotService.Guidance guidance
    ) {
        String brand = blank(command.brandName())
            ? project.getTitle()
            : command.brandName().trim();
        String offer = blank(command.targetOffer())
            ? Optional.ofNullable(project.getDescription()).filter(value -> !value.isBlank())
                .orElse("더 나은 선택")
            : command.targetOffer().trim();
        String personaName = persona == null ? "핵심 고객" : persona.getShortName();
        String guidedHeadline = guidance.headlineSeed();
        String headline = !blank(guidedHeadline) && alternative % 3 == 0
            ? guidedHeadline
            : switch (alternative % 3) {
            case 1 -> personaName + "을 위한 " + offer;
            case 2 -> "지금, " + offer + "을 시작하세요";
            default -> brand + ", " + offer;
        };
        String subheadline = blank(command.emphasisMessage())
            ? purposeLine(command.purpose(), personaName)
            : command.emphasisMessage().trim();
        String body = Optional.ofNullable(project.getDescription())
            .filter(value -> !value.isBlank())
            .orElse("검증 결과를 바탕으로 핵심 고객에게 필요한 가치를 명확하게 전달합니다.");
        if (!blank(command.requiredText())) {
            body = body + " " + command.requiredText().trim();
        }
        if (!blank(guidance.bodySeed())) {
            body = guidance.bodySeed() + " " + body;
        }
        String cta = !blank(command.callToAction())
            ? command.callToAction().trim()
            : !blank(guidance.cta()) ? guidance.cta() : defaultCta(command.purpose());
        Tone tone = command.tone() == null ? recommendedTone(command.purpose()) : command.tone();
        String accent = blank(command.brandColor()) ? "#0f8878" : command.brandColor();
        validateColor(accent);
        Map<String, Object> safe = Map.of(
            "generationMethod", "VALIDATION_TEMPLATE",
            "brandName", brand,
            "personaLabel", personaName,
            "avoidedText", trim(command.avoidedText(), 500),
            "evidence", guidance.evidence(),
            "avoidedExpressions", guidance.avoidedExpressions(),
            "recommendedPresets", guidance.recommendedPresets()
        );
        return new Draft(
            trim(headline, 160),
            trim(subheadline, 240),
            trim(body, 2000),
            trim(cta, 80),
            "#" + brand.replaceAll("\\s+", "") + " #" + personaName.replaceAll("\\s+", ""),
            tone,
            "VALIDATION_DEFAULT",
            command.template() == null ? Template.HERO_CENTER : command.template(),
            BackgroundType.GRADIENT,
            accent + ",#17363a",
            accent,
            "#ffffff",
            TextAlignment.CENTER,
            72,
            true,
            persona != null,
            json(safe)
        );
    }

    private CreateCommand commandFrom(
        MarketingContent content,
        MarketingContentVersion version
    ) {
        return new CreateCommand(
            content.getTitle(),
            content.getPurpose(),
            content.getChannel(),
            content.getFormat(),
            content.getWidth(),
            content.getHeight(),
            content.getSelectedPersona() == null ? null : content.getSelectedPersona().getId(),
            content.getTitle(),
            version.getSubheadline(),
            "",
            "",
            content.getProject().getTitle(),
            version.getAccentColor(),
            version.getCallToAction(),
            version.getVisualStyle(),
            version.getLayoutTemplate(),
            content.getPanelInterviewId(),
            content.getMarketResponseId()
        );
    }

    private MarketingSourceSnapshotService.UserInput sourceInput(
        CreateCommand command
    ) {
        return new MarketingSourceSnapshotService.UserInput(
            trim(command.targetOffer(), 240),
            trim(command.emphasisMessage(), 500),
            trim(command.requiredText(), 500),
            trim(command.avoidedText(), 500),
            trim(command.brandName(), 120),
            trim(command.callToAction(), 80),
            command.tone() == null ? "" : command.tone().name()
        );
    }

    private MarketingSourceSnapshotService.UserInput sourceInput(
        Map<String, Object> value,
        MarketingContentVersion current
    ) {
        return new MarketingSourceSnapshotService.UserInput(
            text(value.get("targetOffer")),
            text(value.get("emphasisMessage")),
            text(value.get("requiredText")),
            text(value.get("avoidedText")),
            text(value.get("brandName")),
            blank(text(value.get("callToAction")))
                ? current.getCallToAction() : text(value.get("callToAction")),
            text(value.get("tone"))
        );
    }

    private Map<String, String> sourceAuditMetadata(
        MarketingSourceSnapshotService.SnapshotResult snapshot
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        if (snapshot.panelInterviewId() != null) {
            result.put("panelInterviewId", snapshot.panelInterviewId().toString());
        }
        if (snapshot.marketResponseId() != null) {
            result.put("marketResponseId", snapshot.marketResponseId().toString());
        }
        result.put("sourceSnapshotVersion", Integer.toString(snapshot.version()));
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private SummaryResponse summary(MarketingContent content) {
        return new SummaryResponse(
            content.getId(),
            content.getTitle(),
            content.getPurpose(),
            content.getChannel(),
            content.getFormat(),
            content.getWidth(),
            content.getHeight(),
            content.getStatus(),
            content.getSelectedPersona() == null ? null : content.getSelectedPersona().getDisplayName(),
            content.getPanelInterviewId(),
            content.getMarketResponseId(),
            content.getSourceSnapshotVersion(),
            content.getCurrentVersion(),
            content.getUpdatedAt()
        );
    }

    private DetailResponse detail(
        MarketingContent content,
        MarketingContentVersion version
    ) {
        return new DetailResponse(
            summary(content),
            content.getVersion(),
            content.getRecommendedPersonaCode(),
            content.getSourceSnapshotJson(),
            sourceSnapshots.legalNotice(content.getSourceSnapshotJson()),
            version(version),
            version.getAnalysisJob() == null
                ? "VALIDATION_TEMPLATE" : "AI_TASK",
            version.getAnalysisJob() != null,
            sourceSnapshots.guidance(content.getSourceSnapshotJson()).evidence(),
            sourceSnapshots.guidance(content.getSourceSnapshotJson()).recommendedPresets()
        );
    }

    private VersionResponse version(MarketingContentVersion value) {
        return new VersionResponse(
            value.getId(),
            value.getVersionNumber(),
            value.getHeadline(),
            value.getSubheadline(),
            value.getBodyCopy(),
            value.getCallToAction(),
            value.getSupportingText(),
            value.getVisualStyle(),
            value.getColorTheme(),
            value.getLayoutTemplate(),
            value.getBackgroundType(),
            value.getBackgroundValue(),
            value.getAccentColor(),
            value.getTextColor(),
            value.getTextAlignment(),
            value.getHeadlineSize(),
            value.isShowCta(),
            value.isShowPersonaTag(),
            value.getContentJson(),
            value.getSourceSnapshotVersion(),
            value.isSourceChanged(),
            value.isCopyChanged(),
            value.getAnalysisJob() == null
                ? null : value.getAnalysisJob().getId(),
            value.getAnalysisJob() != null,
            value.getCreatedAt(),
            value.getUpdatedAt()
        );
    }

    private void audit(
        User actor,
        Project project,
        AuditEventType type,
        MarketingContent content,
        String requestId,
        Map<String, String> additional
    ) {
        Map<String, String> metadata = new LinkedHashMap<>(additional);
        metadata.put("marketingContentId", content.getId().toString());
        audits.record(
            actor.getId(),
            project.getId(),
            type,
            "MARKETING_CONTENT",
            content.getId(),
            requestId,
            metadata
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_SOURCE_UNAVAILABLE);
        }
    }

    private String required(String value, int max) {
        if (blank(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return trim(value, max);
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String purposeLine(Purpose purpose, String personaName) {
        return switch (purpose) {
            case AWARENESS -> personaName + "에게 새로운 가능성을 알립니다";
            case PRODUCT_INTRODUCTION -> personaName + "의 문제를 해결하는 핵심 가치를 소개합니다";
            case EVENT_PROMOTION -> personaName + "이 참여할 이유를 분명하게 전달합니다";
            case LEAD_GENERATION -> personaName + "의 다음 상담과 신청을 연결합니다";
            case CONVERSION -> "검증된 가치로 선택을 돕습니다";
            case RETENTION -> "계속 사용할 이유와 새로운 가치를 전합니다";
        };
    }

    private String defaultCta(Purpose purpose) {
        return switch (purpose) {
            case AWARENESS, PRODUCT_INTRODUCTION -> "자세히 보기";
            case EVENT_PROMOTION -> "지금 참여하기";
            case LEAD_GENERATION -> "상담 신청하기";
            case CONVERSION -> "지금 시작하기";
            case RETENTION -> "혜택 확인하기";
        };
    }

    private Tone recommendedTone(Purpose purpose) {
        return switch (purpose) {
            case AWARENESS -> Tone.BOLD;
            case PRODUCT_INTRODUCTION -> Tone.PROFESSIONAL;
            case EVENT_PROMOTION -> Tone.ENERGETIC;
            case LEAD_GENERATION -> Tone.TRUSTWORTHY;
            case CONVERSION -> Tone.BOLD;
            case RETENTION -> Tone.FRIENDLY;
        };
    }

    private record Size(int width, int height) { }

    public record CreateCommand(
        String title,
        Purpose purpose,
        Channel channel,
        Format format,
        Integer width,
        Integer height,
        Long personaId,
        String targetOffer,
        String emphasisMessage,
        String requiredText,
        String avoidedText,
        String brandName,
        String brandColor,
        String callToAction,
        Tone tone,
        Template template,
        Long panelInterviewId,
        Long marketResponseId
    ) { }

    public record UpdateCommand(
        String title,
        Purpose purpose,
        Channel channel,
        Format format,
        Integer width,
        Integer height,
        Long personaId,
        Long entityVersion,
        Draft draft
    ) { }

    public record SummaryResponse(
        Long id,
        String title,
        Purpose purpose,
        Channel channel,
        Format format,
        int width,
        int height,
        Status status,
        String personaName,
        Long panelInterviewId,
        Long marketResponseId,
        int sourceSnapshotVersion,
        int currentVersion,
        LocalDateTime updatedAt
    ) { }

    public record DetailResponse(
        SummaryResponse content,
        Long entityVersion,
        String recommendedPersonaCode,
        String sourceSnapshotJson,
        String legalNotice,
        VersionResponse current,
        String generationMethod,
        boolean aiGenerated,
        List<String> copyEvidence,
        List<String> recommendedPresets
    ) { }

    public record VersionResponse(
        Long id,
        int versionNumber,
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
        String contentJson,
        int sourceSnapshotVersion,
        boolean sourceChanged,
        boolean copyChanged,
        Long analysisJobId,
        boolean aiGenerated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) { }

    public record DraftResponse(
        Draft draft,
        String generationMethod,
        boolean aiGenerated,
        String legalNotice
    ) { }

    public record SourceRefreshCommand(
        Long panelInterviewId,
        Long marketResponseId,
        boolean generateDraft
    ) { }
}
