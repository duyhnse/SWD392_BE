package swd392.group6.AIVES.ai.mock;

import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.common.Language;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAdaptersTest {

    @Test
    void embeddingIsDeterministicUnitVectorOfConfiguredDimension() {
        MockEmbeddingAdapter embedding = new MockEmbeddingAdapter(1536);

        List<float[]> vectors = embedding.embed(List.of("SOLID", "SOLID", "coupling"));

        assertThat(vectors.get(0)).hasSize(1536).containsExactly(vectors.get(1));
        assertThat(vectors.get(0)).isNotEqualTo(vectors.get(2));
        double norm = 0;
        for (float v : vectors.get(0)) {
            norm += v * v;
        }
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void speechToTextReturnsMockTranscriptWhenGivenElseFixture() {
        MockSpeechToTextAdapter stt = new MockSpeechToTextAdapter();

        assertThat(stt.transcribe(new SpeechToTextRequest(new byte[0], "audio/webm", Language.VI, "", "xin chào")).text())
                .isEqualTo("xin chào");
        assertThat(stt.transcribe(new SpeechToTextRequest(new byte[0], "audio/webm", Language.EN, "", null)).text())
                .isEqualTo(MockSpeechToTextAdapter.FIXTURE_EN);
    }

    @Test
    void textToSpeechReturnsOneSecondOfWav() {
        var audio = new MockTextToSpeechAdapter().synthesize("Xin chào", Language.VI);

        assertThat(audio.contentType()).isEqualTo("audio/wav");
        assertThat(audio.data()).hasSize(44 + 32_000);
        assertThat(new String(audio.data(), 0, 4)).isEqualTo("RIFF");
    }
}
