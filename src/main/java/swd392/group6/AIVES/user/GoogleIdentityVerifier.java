package swd392.group6.AIVES.user;

/** Turns a Google sign-in proof from the browser into a verified Google identity (14 §3.5, D36). */
interface GoogleIdentityVerifier {

    /** @throws swd392.group6.AIVES.common.ApiException 401 GOOGLE_TOKEN_INVALID when the token cannot be trusted */
    GoogleIdentity verify(String idToken);

    /**
     * @param subject stable Google account id ("sub" of the Google identity, not the Firebase uid)
     * @param email   email of the Google account, lowercase
     */
    record GoogleIdentity(String subject, String email, boolean emailVerified) {
    }
}
