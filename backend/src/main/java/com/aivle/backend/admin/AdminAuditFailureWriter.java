package com.aivle.backend.admin;

import com.aivle.backend.audit.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AdminAuditFailureWriter {
    private final AuditEventRepository events;
    private final AdminAuditEventFactory factory;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AdminAuditRecord record) {
        events.save(factory.create(record));
    }
}
