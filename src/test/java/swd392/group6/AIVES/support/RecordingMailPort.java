package swd392.group6.AIVES.support;

import swd392.group6.AIVES.mail.MailMessage;
import swd392.group6.AIVES.mail.MailPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures outgoing mail so tests can read the reset link. */
public class RecordingMailPort implements MailPort {

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        sent.add(message);
    }

    public List<MailMessage> sentTo(String email) {
        return sent.stream().filter(m -> m.to().equals(email)).toList();
    }
}
