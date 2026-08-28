package com.glv.gsysportal.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 7-C3 8章: {{variable}} substitution. Deliberately dumb (no
 * conditionals/loops) - a Mail Template is Subject/Body text with named
 * placeholders, nothing more. Any {{token}} not present in the supplied
 * variable Map is left as-is (visibly unresolved) rather than silently
 * blanked - callers are responsible for ensuring every variable the
 * Template Contract promises (7-C3 8章's list) is always supplied, so an
 * unresolved token in Preview output is a real signal, not expected noise.
 */
@Component
public class MailTemplateRenderer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    public String render(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            matcher.appendReplacement(result, value != null ? Matcher.quoteReplacement(value) : matcher.group(0));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
