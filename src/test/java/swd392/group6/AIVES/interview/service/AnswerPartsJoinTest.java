package swd392.group6.AIVES.interview.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D74 seams, from B-16 (real voice): a word heard at the end of one part and the start of the next is kept once. */
class AnswerPartsJoinTest {

    @Test
    void aWordHeardTwiceAtTheSeamIsKeptOnce() {
        assertThat(AnswerParts.join(List.of("cực kỳ nhanh chóng chỉ với", "với 1 2 tiếng")))
                .isEqualTo("cực kỳ nhanh chóng chỉ với 1 2 tiếng");
        assertThat(AnswerParts.join(List.of("Ví dụ như trẻ em sinh", "sinh ra hàng năm."))).isEqualTo("Ví dụ như trẻ em sinh ra hàng năm.");
        assertThat(AnswerParts.join(List.of("theo Hải Phòng.", "Hải Phòng, giao thông"))).isEqualTo("theo Hải Phòng. giao thông");
    }

    @Test
    void differentWordsAndEmptyPartsJoinPlainly() {
        assertThat(AnswerParts.join(List.of("", "3/4 diện tích", "", "là đồi núi"))).isEqualTo("3/4 diện tích là đồi núi");
        assertThat(AnswerParts.join(List.of())).isEmpty();
    }
}
