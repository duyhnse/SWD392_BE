package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Only Google's photo host is ever contacted (no request to arbitrary URLs from a token claim). */
class GoogleAvatarFetcherTest {

    @Test
    void acceptsGooglePhotoUrlsAndAsksFor512px() {
        assertThat(GoogleAvatarFetcher.allowed("https://lh3.googleusercontent.com/a/ACg8oc=s96-c"))
                .hasToString("https://lh3.googleusercontent.com/a/ACg8oc=s512-c");
    }

    @Test
    void rejectsOtherHostsAndPlainHttp() {
        assertThat(GoogleAvatarFetcher.allowed("http://lh3.googleusercontent.com/a/x")).isNull();
        assertThat(GoogleAvatarFetcher.allowed("https://evil.example.com/a.png")).isNull();
        assertThat(GoogleAvatarFetcher.allowed("https://googleusercontent.com.evil.io/a.png")).isNull();
        assertThat(GoogleAvatarFetcher.allowed("https://169.254.169.254/latest")).isNull();
        assertThat(GoogleAvatarFetcher.allowed(null)).isNull();
    }
}
