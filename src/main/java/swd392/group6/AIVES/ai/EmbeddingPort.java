package swd392.group6.AIVES.ai;

import java.util.List;

public interface EmbeddingPort {

    /** One vector per input text, in the same order. Length of each vector = {@link #dimension()}. */
    List<float[]> embed(List<String> texts);

    /** Must equal the {@code vector(n)} size of material_chunks.embedding. */
    int dimension();
}
