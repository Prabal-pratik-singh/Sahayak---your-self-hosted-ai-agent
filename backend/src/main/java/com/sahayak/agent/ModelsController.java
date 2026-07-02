package com.sahayak.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tells the UI which AI brains this server has and which one is the default. */
@RestController
@RequestMapping("/api")
public class ModelsController {

    public record ModelsResponse(String defaultId, List<AiModelRegistry.Option> options) {
    }

    private final AiModelRegistry registry;

    public ModelsController(AiModelRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/models")
    public ModelsResponse models() {
        return new ModelsResponse(registry.defaultId(), registry.options());
    }
}
