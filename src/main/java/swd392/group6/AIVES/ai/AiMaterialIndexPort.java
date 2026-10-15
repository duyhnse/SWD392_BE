package swd392.group6.AIVES.ai;

import java.util.UUID;

/** The AI node keeps the RAG index of course materials (16 §4.1); node 1 only tells it when a material is gone. */
public interface AiMaterialIndexPort {

    /** Best effort and idempotent: drop the material's passages from the AI node's index. */
    void deleteMaterial(UUID materialId);
}
