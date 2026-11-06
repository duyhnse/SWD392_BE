package swd392.group6.AIVES.mail;

import lombok.extern.slf4j.Slf4j;

/** Development/demo mode: the message, including any link, is written to the application log. */
@Slf4j
class LogMailAdapter implements MailPort {

    @Override
    public void send(MailMessage message) {
        log.info("MAIL (log mode, not sent) to={} subject=\"{}\"\n{}", message.to(), message.subject(), message.text());
    }
}
