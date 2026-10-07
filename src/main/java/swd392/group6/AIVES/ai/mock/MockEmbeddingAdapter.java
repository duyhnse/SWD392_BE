package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.EmbeddingPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

/** Stable pseudo-embedding: same text → same unit vector. Lets pgvector queries run without a provider. */
public class MockEmbeddingAdapter implements EmbeddingPort {

    private final int dimension;

    public MockEmbeddingAdapter(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embedOne).toList();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private float[] embedOne(String text) {
        Random random = new Random(seed(text));
        float[] vector = new float[dimension];
        double norm = 0;
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) random.nextGaussian();
            norm += vector[i] * vector[i];
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < dimension; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    private static long seed(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xff);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
