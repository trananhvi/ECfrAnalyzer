package com.vitran.ecfranalyzer.controller;

import com.vitran.ecfranalyzer.model.AgencyMetrics;
import com.vitran.ecfranalyzer.model.AnalysisReport;
import com.vitran.ecfranalyzer.service.AnalyticsService;
import com.vitran.ecfranalyzer.service.DataSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private DataSyncService dataSyncService;

    @GetMapping("/report")
    public ResponseEntity<AnalysisReport> getAnalysisReport() {
        AnalysisReport report = analyticsService.generateAnalysisReport();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/agencies")
    public ResponseEntity<List<AgencyMetrics>> getAgencies(
            @RequestParam(defaultValue = "regulations") String sortBy,
            @RequestParam(defaultValue = "50") int limit) {

        List<AgencyMetrics> agencies = analyticsService.getTopAgenciesByMetric(sortBy, limit);
        return ResponseEntity.ok(agencies);
    }

    @GetMapping("/agencies/top")
    public ResponseEntity<Map<String, List<AgencyMetrics>>> getTopAgencies(
            @RequestParam(defaultValue = "5") int limit) {

        Map<String, List<AgencyMetrics>> topAgencies = new HashMap<>();
        topAgencies.put("regulations", analyticsService.getTopAgenciesByMetric("regulations", limit));
        topAgencies.put("words", analyticsService.getTopAgenciesByMetric("words", limit));
        topAgencies.put("complexity", analyticsService.getTopAgenciesByMetric("complexity", limit));

        return ResponseEntity.ok(topAgencies);
    }

    @PostMapping("/sync/force")
    public ResponseEntity<Map<String, Object>> forceSync() {
        Map<String, Object> response = new HashMap<>();

        if (dataSyncService.isSyncInProgress()) {
            response.put("status", "error");
            response.put("message", "Sync already in progress");
            return ResponseEntity.badRequest().body(response);
        }

        dataSyncService.forceSync();
        response.put("status", "success");
        response.put("message", "Sync initiated");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isSyncing", dataSyncService.isSyncInProgress());
        status.put("lastSync", dataSyncService.getLastSyncTime());

        return ResponseEntity.ok(status);
    }
}
