package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.SynthesizedAudio;
import swd392.group6.AIVES.ai.TextToSpeechPort;
import swd392.group6.AIVES.common.Language;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Returns one second of silence as WAV; the frontend still shows the question text. */
public class MockTextToSpeechAdapter implements TextToSpeechPort {

    private static final int SAMPLE_RATE = 16_000;
    private static final byte[] SILENCE = silentWav(SAMPLE_RATE);

    @Override
    public SynthesizedAudio synthesize(String text, Language language) {
        return new SynthesizedAudio(SILENCE.clone(), "audio/wav");
    }

    private static byte[] silentWav(int samples) {
        int dataBytes = samples * 2; // 16-bit mono
        ByteBuffer wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        wav.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
        wav.put("data".getBytes()).putInt(dataBytes);
        return wav.array(); // remaining bytes are already zero = silence
    }
}
