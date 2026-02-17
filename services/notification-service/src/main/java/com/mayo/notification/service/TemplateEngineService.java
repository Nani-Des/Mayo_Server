package com.mayo.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationTemplate;
import com.mayo.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateEngineService {

    private final NotificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    public Notification applyTemplate(Notification notification, Map<String, String> templateData) {
        if (notification.getTemplateId() == null) {
            return notification;
        }

        Optional<NotificationTemplate> templateOpt = templateRepository.findByIdAndActive(notification.getTemplateId(), true);
        if (templateOpt.isEmpty()) {
            log.warn("Template not found or inactive: {}", notification.getTemplateId());
            return notification;
        }

        NotificationTemplate template = templateOpt.get();

        // Get user language (default to "en")
        String language = getUserLanguage(notification);

        // Get localized content
        Map<String, String> localizedContent = getLocalizedContent(template, language);

        // Merge template data
        Map<String, Object> context = new HashMap<>();
        if (templateData != null) {
            context.putAll(templateData);
        }
        if (notification.getTemplateData() != null) {
            context.putAll(notification.getTemplateData());
        }

        // Render title and message
        String renderedTitle = renderTemplate(localizedContent.get("title"), context);
        String renderedMessage = renderTemplate(localizedContent.get("message"), context);

        return notification.toBuilder()
                .title(renderedTitle != null ? renderedTitle : notification.getTitle())
                .message(renderedMessage != null ? renderedMessage : notification.getMessage())
                .build();
    }

    @Cacheable(value = "notificationTemplates", key = "#templateId + '_' + #language")
    public Map<String, String> getLocalizedContent(NotificationTemplate template, String language) {
        Map<String, String> localizations = template.getLocalizations();
        if (localizations == null || localizations.isEmpty()) {
            return Map.of();
        }

        // Try exact language match
        String content = localizations.get(language);
        if (content != null) {
            return parseLocalizedContent(content);
        }

        // Try language prefix (e.g., "en" for "en-US")
        String languagePrefix = language.split("-")[0];
        content = localizations.get(languagePrefix);
        if (content != null) {
            return parseLocalizedContent(content);
        }

        // Fallback to English
        content = localizations.get("en");
        if (content != null) {
            return parseLocalizedContent(content);
        }

        // Return first available localization
        return parseLocalizedContent(localizations.values().iterator().next());
    }

    private Map<String, String> parseLocalizedContent(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            Map<String, String> result = new HashMap<>();
            if (node.has("title")) {
                result.put("title", node.get("title").asText());
            }
            if (node.has("message")) {
                result.put("message", node.get("message").asText());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse localized content: {}", content, e);
            return Map.of();
        }
    }

    private String renderTemplate(String template, Map<String, Object> context) {
        if (template == null || template.trim().isEmpty()) {
            return null;
        }

        try {
            Mustache mustache = mustacheFactory.compile(new StringReader(template), "template");
            StringWriter writer = new StringWriter();
            mustache.execute(writer, context);
            return writer.toString();
        } catch (Exception e) {
            log.error("Failed to render template: {}", template, e);
            return template; // Return original template on error
        }
    }

    private String getUserLanguage(Notification notification) {
        // In a real implementation, this would get the user's language preference
        // For now, return default
        return "en";
    }
}