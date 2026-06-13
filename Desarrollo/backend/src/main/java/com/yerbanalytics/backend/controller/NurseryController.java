package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.NurseryData;
import com.yerbanalytics.backend.service.NurseryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nursery")
public class NurseryController {

    private final NurseryService nurseryService;

    public NurseryController(NurseryService nurseryService) {
        this.nurseryService = nurseryService;
    }

    @GetMapping
    public ResponseEntity<NurseryData> getNurseryData() {
        return ResponseEntity.ok(nurseryService.getSnapshot());
    }
}