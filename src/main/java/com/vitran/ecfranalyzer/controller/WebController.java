package com.vitran.ecfranalyzer.controller;

import com.vitran.ecfranalyzer.model.AgencyMetrics;
import com.vitran.ecfranalyzer.model.AnalysisReport;
import com.vitran.ecfranalyzer.service.AnalyticsService;
import com.vitran.ecfranalyzer.service.DataSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WebController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private DataSyncService dataSyncService;

    @GetMapping("/")
    public String dashboard(Model model) {
        AnalysisReport report = analyticsService.generateAnalysisReport();

        model.addAttribute("report", report);
        model.addAttribute("isSyncing", dataSyncService.isSyncInProgress());
        model.addAttribute("lastSync", dataSyncService.getLastSyncTime());

        // Get top agencies for quick overview
        List<AgencyMetrics> topByRegulations = analyticsService.getTopAgenciesByMetric("regulations", 5);
        List<AgencyMetrics> topByWords = analyticsService.getTopAgenciesByMetric("words", 5);
        List<AgencyMetrics> topByComplexity = analyticsService.getTopAgenciesByMetric("complexity", 5);

        model.addAttribute("topByRegulations", topByRegulations);
        model.addAttribute("topByWords", topByWords);
        model.addAttribute("topByComplexity", topByComplexity);

        return "dashboard";
    }

    @GetMapping("/agencies")
    public String agencies(Model model,
                          @RequestParam(defaultValue = "regulations") String sortBy,
                          @RequestParam(defaultValue = "20") int limit) {

        List<AgencyMetrics> agencies = analyticsService.getTopAgenciesByMetric(sortBy, limit);

        model.addAttribute("agencies", agencies);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("limit", limit);

        return "agencies";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
