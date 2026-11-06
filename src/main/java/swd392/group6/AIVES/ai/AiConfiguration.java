package swd392.group6.AIVES.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code application.ai-node.*}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiNodeProperties.class)
class AiConfiguration {
}
