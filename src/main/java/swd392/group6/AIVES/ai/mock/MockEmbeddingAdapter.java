package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.EmbeddingPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Stable bag-of-words pseudo-embedding (feature hashing): every word adds its count to one signed dimension, then the
 * vector is normalised. Same text → same unit vector; texts that share words are close and unrelated texts are near
 * 0, so pgvector retrieval and the "not enough relevant context" rule (04 §9.2, E1) behave sensibly without a
 * provider.
 */
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
        float[] vector = new float[dimension];
        List<String> words = words(text);
        for (String word : words.isEmpty() ? List.of(text == null ? "" : text) : words) {
            long h = seed(word);
            int index = (int) Math.floorMod(h, (long) dimension);
            vector[index] += (h >>> 63) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        float scale = norm == 0 ? 0 : (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < dimension; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    /** Lower-case words without diacritics, at least 2 letters ("Kiến trúc" → kien, truc). */
    static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        String plain = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(plain.split("[^\\p{L}\\p{N}]+")).filter(w -> w.length() >= 2).toList();
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
