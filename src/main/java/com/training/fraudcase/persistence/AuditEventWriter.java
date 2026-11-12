package com.training.fraudcase.persistence;

import com.training.fraudcase.domain.enums.ActorType;
import com.training.fraudcase.persistence.entity.AuditEventEntity;
import com.training.fraudcase.persistence.repo.AuditEventRepository;
import com.training.fraudcase.support.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit writer. Records event type, actor and a payload hash —
 * never raw sensitive payloads.
 */
@Component
public class AuditEventWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditEventWriter.class);

    private final AuditEventRepository repository;

    public AuditEventWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    public String append(String caseId, String eventType, ActorType actorType, String summary) {
        String safeSummary = summary == null ? "" : (summary.length() > 1000 ? summary.substring(0, 1000) : summary);
        AuditEventEntity entity = new AuditEventEntity();
        String eventId = "EV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        entity.setEventId(eventId);
        entity.setCaseId(caseId);
        entity.setEventType(eventType);
        entity.setActorType(actorType);
        entity.setTimestamp(Instant.now());
        entity.setPayloadHash(Hashes.sha256(caseId + "|" + eventType + "|" + safeSummary));
        entity.setSummary(safeSummary);
        repository.save(entity);
        log.debug("audit caseId={} type={} id={}", caseId, eventType, eventId);
        return eventId;
    }
}
