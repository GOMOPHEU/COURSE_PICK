package com.coursepick.controller;

import com.coursepick.candidate.CandidateRequest;
import com.coursepick.candidate.CandidateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class CandidateController {
    private final CandidateService service;
    public CandidateController(CandidateService service) { this.service = service; }
    @PostMapping("/candidates")
    public CandidateService.Result candidates(@Valid @RequestBody CandidateRequest request) {
        return service.find(request);
    }
}
