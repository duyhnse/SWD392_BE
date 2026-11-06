package swd392.group6.AIVES.mail;

public interface MailPort {

    /** Sends (or, in log mode, records) one message. Failures are logged, never thrown to the caller. */
    void send(MailMessage message);
}
