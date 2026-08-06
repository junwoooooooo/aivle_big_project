package com.aivle.backend.marketing.content;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.marketing.content.MarketingContentService.*;
import com.aivle.backend.marketing.content.MarketingContentVersion.Draft;
import com.aivle.backend.marketing.generation.MarketingGenerationCommandService;
import com.aivle.backend.marketing.generation.MarketingGenerationStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.aivle.backend.marketing.content.MarketingContentTypes.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/marketing-contents")
@RequiredArgsConstructor
public class MarketingContentController {
    private final MarketingContentService marketing;
    private final CurrentUserProvider currentUser;
    private final MarketingGenerationCommandService generation;

    @GetMapping
    public ApiResponse<List<SummaryResponse>> list(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.list(currentUser.currentUserId(), projectId),
            requestId(request)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DetailResponse> create(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.create(
                currentUser.currentUserId(),
                projectId,
                body.command(),
                requestId(request)
            ),
            requestId(request)
        );
    }

    @GetMapping("/{contentId}")
    public ApiResponse<DetailResponse> detail(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.detail(currentUser.currentUserId(), projectId, contentId),
            requestId(request)
        );
    }

    @PatchMapping("/{contentId}")
    public ApiResponse<DetailResponse> update(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        @Valid @RequestBody UpdateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.update(
                currentUser.currentUserId(),
                projectId,
                contentId,
                body.command(),
                requestId(request)
            ),
            requestId(request)
        );
    }

    @DeleteMapping("/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        HttpServletRequest request
    ) {
        marketing.delete(
            currentUser.currentUserId(),
            projectId,
            contentId,
            requestId(request)
        );
    }

    @PostMapping("/{contentId}/draft-copy")
    public ApiResponse<DraftResponse> alternateDraft(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        @RequestParam(defaultValue = "0") int alternative,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.alternateDraft(
                currentUser.currentUserId(),
                projectId,
                contentId,
                Math.max(0, alternative)
            ),
            requestId(request)
        );
    }

    @PostMapping("/{contentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VersionResponse> createVersion(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        @Valid @RequestBody DraftRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.createVersion(
                currentUser.currentUserId(),
                projectId,
                contentId,
                body.draft(),
                requestId(request)
            ),
            requestId(request)
        );
    }

    @PostMapping("/{contentId}/source-refresh")
    public ApiResponse<DetailResponse> refreshSource(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        @Valid @RequestBody SourceRefreshRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.refreshSource(
                currentUser.currentUserId(),
                projectId,
                contentId,
                body.command(),
                requestId(request)
            ),
            requestId(request)
        );
    }

    @GetMapping("/{contentId}/versions")
    public ApiResponse<List<VersionResponse>> versions(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            marketing.versions(currentUser.currentUserId(), projectId, contentId),
            requestId(request)
        );
    }

    @PostMapping(
        value = "/{contentId}/generate",
        consumes = "multipart/form-data"
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<MarketingGenerationStartResponse> generate(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        @RequestParam(required = false) Long sourceVersionId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestPart("image") MultipartFile image,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            generation.start(
                currentUser.currentUserId(), projectId, contentId,
                sourceVersionId, idempotencyKey, image),
            requestId(request)
        );
    }

    @PostMapping("/{contentId}/rerun")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<MarketingGenerationStartResponse> rerun(
        @PathVariable Long projectId,
        @PathVariable Long contentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody RerunRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            generation.rerun(
                currentUser.currentUserId(), projectId, contentId,
                body.originalJobId(), idempotencyKey),
            requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Purpose purpose,
        @NotNull Channel channel,
        @NotNull Format format,
        @Min(320) @Max(4096) Integer width,
        @Min(320) @Max(4096) Integer height,
        Long personaId,
        @NotBlank @Size(max = 240) String targetOffer,
        @Size(max = 500) String emphasisMessage,
        @Size(max = 500) String requiredText,
        @Size(max = 500) String avoidedText,
        @Size(max = 120) String brandName,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String brandColor,
        @Size(max = 80) String callToAction,
        Tone tone,
        Template template,
        @Positive Long panelInterviewId,
        @Positive Long marketResponseId
    ) {
        CreateCommand command() {
            return new CreateCommand(
                title, purpose, channel, format, width, height, personaId,
                targetOffer, emphasisMessage, requiredText, avoidedText,
                brandName, brandColor, callToAction, tone, template,
                panelInterviewId, marketResponseId
            );
        }
    }

    public record SourceRefreshRequest(
        @Positive Long panelInterviewId,
        @Positive Long marketResponseId,
        boolean generateDraft
    ) {
        SourceRefreshCommand command() {
            return new SourceRefreshCommand(
                panelInterviewId,
                marketResponseId,
                generateDraft
            );
        }
    }

    public record UpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Purpose purpose,
        @NotNull Channel channel,
        @NotNull Format format,
        @Min(320) @Max(4096) Integer width,
        @Min(320) @Max(4096) Integer height,
        Long personaId,
        Long entityVersion,
        @Valid @NotNull DraftRequest draft
    ) {
        UpdateCommand command() {
            return new UpdateCommand(
                title, purpose, channel, format, width, height,
                personaId, entityVersion, draft.draft()
            );
        }
    }

    public record DraftRequest(
        @NotBlank @Size(max = 160) String headline,
        @Size(max = 240) String subheadline,
        @Size(max = 2000) String bodyCopy,
        @Size(max = 80) String callToAction,
        @Size(max = 240) String supportingText,
        @NotNull Tone visualStyle,
        @NotBlank @Size(max = 30) String colorTheme,
        @NotNull Template layoutTemplate,
        @NotNull BackgroundType backgroundType,
        @Size(max = 500) String backgroundValue,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String accentColor,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String textColor,
        @NotNull TextAlignment textAlignment,
        @Min(28) @Max(180) int headlineSize,
        boolean showCta,
        boolean showPersonaTag,
        @Size(max = 4000) String contentJson
    ) {
        Draft draft() {
            return new Draft(
                headline, subheadline, bodyCopy, callToAction, supportingText,
                visualStyle, colorTheme, layoutTemplate, backgroundType,
                backgroundValue, accentColor, textColor, textAlignment,
                headlineSize, showCta, showPersonaTag, contentJson
            );
        }
    }

    public record RerunRequest(@NotNull @Positive Long originalJobId) {
    }
}
