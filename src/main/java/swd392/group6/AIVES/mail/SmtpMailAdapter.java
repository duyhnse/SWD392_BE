package swd392.group6.AIVES.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Slf4j
class SmtpMailAdapter implements MailPort {

    private final JavaMailSender sender;
    private final String from;

    SmtpMailAdapter(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        try {
            sender.send(mail);
        } catch (MailException e) {
            // Never reveal delivery problems to the requester (BR-A1); operators see them in the log.
            log.error("Could not send mail \"{}\" to {}", message.subject(), message.to(), e);
        }
    }
}
