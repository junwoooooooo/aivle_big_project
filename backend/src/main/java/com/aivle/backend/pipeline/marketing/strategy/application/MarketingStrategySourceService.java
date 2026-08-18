package com.aivle.backend.pipeline.marketing.strategy.application;

import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingStrategySourceService {

    private static final Set<String> INCLUDED = Set.of(
        "PROJECT",
        "CURRENT_CONCEPT",
        "MARKET",
        "BUSINESS_MODEL",
        "LAUNCH_TECHNOLOGY",
        "LAUNCH_OPERATIONS",
        "FINANCE",
        "FINANCE_REPORT",
        "MARKET_INTERVIEW"
    );

    private static final List<String> REQUIRED = List.of(
        "CURRENT_CONCEPT"
    );

    private static final List<String> OPTIONAL = List.of(
        "MARKET", "BUSINESS_MODEL", "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS",
        "FINANCE", "FINANCE_REPORT", "MARKET_INTERVIEW"
    );

    private final FinalReportService finalReports;
    private final SnapshotHasher snapshotHasher;
    private final ObjectMapper mapper;

    public SourceBundle inspect(
        Long ownerId,
        Long projectId
    ) {
        var catalog = finalReports.currentSourceCatalog(ownerId, projectId);

        ArrayNode manifest = mapper.createArrayNode();

        JsonNode sourceItems = catalog.manifest();
        if (sourceItems.isArray()) {
            for (JsonNode item : sourceItems) {
                String type = item.path("type").asText();

                if (INCLUDED.contains(type)) {
                    manifest.add(item.deepCopy());
                }
            }
        }

        ObjectNode sources = mapper.createObjectNode();
        for (String type : INCLUDED) {
            if (catalog.sources().has(type)) {
                sources.set(type, catalog.sources().path(type).deepCopy());
            }
        }

        List<String> requiredMissing = REQUIRED.stream()
            .filter(type -> !sources.has(type))
            .toList();
        List<String> missing = java.util.stream.Stream.concat(
            requiredMissing.stream(), OPTIONAL.stream().filter(type -> !sources.has(type))
        ).distinct().toList();

        String hash = snapshotHasher.hash(manifest);

        return new SourceBundle(
            manifest,
            sources,
            hash,
            missing,
            requiredMissing.isEmpty()
        );
    }

    public record SourceBundle(
        ArrayNode manifest,
        ObjectNode sources,
        String hash,
        List<String> missing,
        boolean ready
    ) {
        public boolean ready() {
            return ready;
        }

        public ObjectNode toInput(
            ObjectMapper mapper,
            Long projectId
        ) {
            ObjectNode input = mapper.createObjectNode();

            input.put(
                "contract",
                "marketing-strategy-input-v1"
            );
            input.put("projectId", projectId);
            input.put("sourceManifestHash", hash);
            input.set(
                "sourceManifest",
                manifest.deepCopy()
            );
            input.set(
                "sources",
                sources.deepCopy()
            );

            return input;
        }
    }
}
