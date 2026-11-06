package swd392.group6.AIVES.questionbank.service;

import swd392.group6.AIVES.questionbank.domain.CourseMaterial;
import swd392.group6.AIVES.questionbank.domain.MaterialStatus;
import swd392.group6.AIVES.questionbank.repository.CourseMaterialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import swd392.group6.AIVES.ai.AiEmbeddingModelChangedEvent;

/** A new embedding model makes the old index unusable (the AI node ignores vectors of another model): index again (D70). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialReindexer {

    private final CourseMaterialRepository materials;
    private final MaterialService service;

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(AiEmbeddingModelChangedEvent e) {
        int n = 0;
        for (CourseMaterial m : materials.findAll()) {
            if (m.getStatus() == MaterialStatus.INDEXED || m.getStatus() == MaterialStatus.FAILED) {
                service.startIndexing(m);
                n++;
            }
        }
        log.info("Embedding model is now {}: {} material(s) queued for indexing", e.model(), n);
    }
}
