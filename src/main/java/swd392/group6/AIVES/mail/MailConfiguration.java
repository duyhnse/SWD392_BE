package swd392.group6.AIVES.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
class MailConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "application.mail", name = "mode", havingValue = "log", matchIfMissing = true)
    MailPort logMail() {
        return new LogMailAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.mail", name = "mode", havingValue = "smtp")
    MailPort smtpMail(JavaMailSender sender, @Value("${application.mail.from}") String from) {
        return new SmtpMailAdapter(sender, from);
    }
}
