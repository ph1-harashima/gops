package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.AttentionResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.AttentionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttentionController {

    private final AttentionService attentionService;
    private final CurrentUserProvider currentUserProvider;

    public AttentionController(AttentionService attentionService, CurrentUserProvider currentUserProvider) {
        this.attentionService = attentionService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/attentions/{id}/acknowledge")
    public AttentionResponse acknowledge(@PathVariable Long id) {
        return attentionService.acknowledge(id, currentUserProvider.currentUsername());
    }
}
