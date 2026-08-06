package com.aivle.backend.journey.foundation;

import com.aivle.backend.journey.boundary.RegulatoryBoundaryVersion;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceStaleService {
    public boolean boundaryIsStale(OpportunityBriefVersion currentBrief,
            RegulatoryBoundaryVersion boundaryVersion) {
        if (currentBrief == null || boundaryVersion == null) return true;
        return !Objects.equals(currentBrief.getId(), boundaryVersion.getBriefVersion().getId())
            || !Objects.equals(currentBrief.getSnapshotHash(),
                boundaryVersion.getRun().getInputSnapshotHash());
    }

    public StaleCascade afterBriefChange() {
        return new StaleCascade(true, true, true, true);
    }

    public StaleCascade afterBoundaryChange() {
        return new StaleCascade(false, true, true, true);
    }

    public record StaleCascade(boolean boundary, boolean concepts,
                               boolean quickAssessment, boolean selection) { }
}
