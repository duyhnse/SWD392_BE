package swd392.group6.AIVES.ai.web;

import swd392.group6.AIVES.ai.AiCatalogPort;
import swd392.group6.AIVES.ai.AiSettings;
import swd392.group6.AIVES.ai.AiUnavailableException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.ApiException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.function.Function;

/** Admin → Settings → AI (D70): the rules of the {@code ai.*} settings and what the AI node offers. */
@RestController
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiController {

    private final ObjectProvider<AiCatalogPort> catalog;

    AdminAiController(ObjectProvider<AiCatalogPort> catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/rules")
    public List<AiSettings.Rule> rules() {
        return AiSettings.RULES;
    }

    @GetMapping("/models")
    public JsonNode models() {
        return ask(AiCatalogPort::models);
    }

    @GetMapping("/health")
    public JsonNode health() {
        return ask(AiCatalogPort::health);
    }

    private JsonNode ask(Function<AiCatalogPort, JsonNode> call) {
        AiCatalogPort port = catalog.getIfAvailable();
        if (port == null) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AI_NODE_UNAVAILABLE",
                    "The AI node is not configured");
        }
        try {
            return call.apply(port);
        } catch (AiUnavailableException e) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AI_NODE_UNAVAILABLE",
                    "The AI node does not answer: " + e.getMessage());
        }
    }
}
