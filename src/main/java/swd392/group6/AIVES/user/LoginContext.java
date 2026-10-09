package swd392.group6.AIVES.user;

/**
 * Who is logging in from where (D38).
 *
 * @param deviceId random id the browser keeps in localStorage — the same browser re-logging in is not asked to
 *                 sign out "the other device". Client-provided, so it only skips the prompt, never grants access.
 * @param force    the user confirmed signing out the session on the other device
 */
record LoginContext(String deviceId, String userAgent, String ip, boolean force) {

    static LoginContext of(jakarta.servlet.http.HttpServletRequest http, String deviceId, Boolean force) {
        String ua = http.getHeader("User-Agent");
        return new LoginContext(blankToNull(deviceId, 64), blankToNull(ua, 300), http.getRemoteAddr(), Boolean.TRUE.equals(force));
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
