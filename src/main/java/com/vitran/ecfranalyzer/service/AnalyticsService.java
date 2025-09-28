package com.vitran.ecfranalyzer.service;

import com.vitran.ecfranalyzer.model.AgencyMetrics;
import com.vitran.ecfranalyzer.model.AnalysisReport;
import com.vitran.ecfranalyzer.model.ECFRTitle;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    @Autowired
    private DataStorageService dataStorageService;

    public AnalysisReport generateAnalysisReport() {
        logger.info("Generating comprehensive analysis report");

        List<ECFRTitle> allTitles = dataStorageService.loadTitles();
        AnalysisReport report = new AnalysisReport();

        // Basic statistics
        report.setTotalRegulations(allTitles.size());
        report.setTotalWordCount(allTitles.stream().mapToLong(ECFRTitle::getWordCount).sum());
        report.setLastDataUpdate(getLatestUpdateTime(allTitles));

        // Generate agency metrics
        Map<String, AgencyMetrics> agencyMetrics = generateAgencyMetrics(allTitles);
        report.setAgencyMetrics(agencyMetrics);
        report.setTotalAgencies(agencyMetrics.size());

        // Find top agencies
        setTopAgencies(report, agencyMetrics);

        // Generate overall checksum
        report.setOverallChecksum(generateOverallChecksum(allTitles));

        logger.info("Analysis report generated with {} agencies and {} regulations",
                   report.getTotalAgencies(), report.getTotalRegulations());

        return report;
    }

    public Map<String, AgencyMetrics> generateAgencyMetrics(List<ECFRTitle> titles) {
        Map<String, List<ECFRTitle>> agencyGroups = titles.stream()
                .filter(title -> StringUtils.isNotBlank(title.getAgency()))
                .collect(Collectors.groupingBy(ECFRTitle::getAgency));

        Map<String, AgencyMetrics> metricsMap = new HashMap<>();

        for (Map.Entry<String, List<ECFRTitle>> entry : agencyGroups.entrySet()) {
            String agency = entry.getKey();
            List<ECFRTitle> agencyTitles = entry.getValue();

            AgencyMetrics metrics = calculateAgencyMetrics(agency, agencyTitles);
            metricsMap.put(agency, metrics);
        }

        return metricsMap;
    }

    private AgencyMetrics calculateAgencyMetrics(String agency, List<ECFRTitle> titles) {
        AgencyMetrics metrics = new AgencyMetrics(agency);

        // Basic counts
        metrics.setTotalRegulations(titles.size());
        metrics.setTotalWordCount(titles.stream().mapToLong(ECFRTitle::getWordCount).sum());

        // Unique titles count
        Set<String> uniqueTitles = titles.stream()
                .map(ECFRTitle::getTitleNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        metrics.setUniqueTitles(uniqueTitles.size());

        // Calculate custom Regulatory Complexity Index (RCI)
        double complexityIndex = calculateRegulatoryComplexityIndex(titles);
        metrics.setRegulatoryComplexityIndex(complexityIndex);

        // Generate agency checksum
        metrics.setChecksum(generateAgencyChecksum(titles));
        metrics.setLastUpdated(LocalDateTime.now());

        return metrics;
    }

    /**
     * Custom Metric: Regulatory Complexity Index (RCI)
     *
     * This metric combines multiple factors to assess the complexity of regulations:
     * - Average words per regulation (verbosity factor)
     * - Number of different titles covered (scope factor)
     * - Text density and structure complexity
     *
     * Higher values indicate more complex regulatory frameworks that may benefit
     * from streamlining or deregulation efforts.
     *
     * Formula: RCI = (avgWordsPerReg/1000) * (uniqueTitles/10) * structureComplexity
     */
    private double calculateRegulatoryComplexityIndex(List<ECFRTitle> titles) {
        if (titles.isEmpty()) return 0.0;

        // Factor 1: Verbosity (average words per regulation)
        double avgWords = titles.stream().mapToDouble(ECFRTitle::getWordCount).average().orElse(0.0);
        double verbosityFactor = avgWords / 1000.0; // Normalize to reasonable scale

        // Factor 2: Scope (number of unique titles)
        long uniqueTitles = titles.stream()
                .map(ECFRTitle::getTitleNumber)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        double scopeFactor = uniqueTitles / 10.0; // Normalize

        // Factor 3: Structure complexity (based on sections, parts, chapters)
        double structureFactor = calculateStructureComplexity(titles);

        // Combine factors with weights
        double rci = (verbosityFactor * 0.4) + (scopeFactor * 0.3) + (structureFactor * 0.3);

        return Math.round(rci * 100.0) / 100.0; // Round to 2 decimal places
    }

    private double calculateStructureComplexity(List<ECFRTitle> titles) {
        Set<String> uniqueParts = new HashSet<>();
        Set<String> uniqueChapters = new HashSet<>();
        Set<String> uniqueSections = new HashSet<>();

        for (ECFRTitle title : titles) {
            if (StringUtils.isNotBlank(title.getPart())) uniqueParts.add(title.getPart());
            if (StringUtils.isNotBlank(title.getChapter())) uniqueChapters.add(title.getChapter());
            if (StringUtils.isNotBlank(title.getSection())) uniqueSections.add(title.getSection());
        }

        // More structural elements indicate higher complexity
        return (uniqueParts.size() + uniqueChapters.size() + uniqueSections.size()) / 100.0;
    }

    private void setTopAgencies(AnalysisReport report, Map<String, AgencyMetrics> agencyMetrics) {
        // Most regulations
        agencyMetrics.entrySet().stream()
                .max(Comparator.comparing(e -> e.getValue().getTotalRegulations()))
                .ifPresent(e -> report.setMostRegulationsAgency(e.getKey()));

        // Most words
        agencyMetrics.entrySet().stream()
                .max(Comparator.comparing(e -> e.getValue().getTotalWordCount()))
                .ifPresent(e -> report.setMostWordsAgency(e.getKey()));

        // Highest complexity
        agencyMetrics.entrySet().stream()
                .max(Comparator.comparing(e -> e.getValue().getRegulatoryComplexityIndex()))
                .ifPresent(e -> report.setHighestComplexityAgency(e.getKey()));
    }

    private String generateAgencyChecksum(List<ECFRTitle> titles) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            String content = titles.stream()
                    .sorted(Comparator.comparing(ECFRTitle::getTitleNumber, Comparator.nullsLast(String::compareTo)))
                    .map(title -> title.getTitleNumber() + title.getTitleName() + title.getWordCount())
                    .collect(Collectors.joining());

            byte[] hash = md.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            logger.warn("Failed to generate checksum", e);
            return String.valueOf(titles.hashCode());
        }
    }

    private String generateOverallChecksum(List<ECFRTitle> titles) {
        return generateAgencyChecksum(titles); // Same algorithm for overall checksum
    }

    private LocalDateTime getLatestUpdateTime(List<ECFRTitle> titles) {
        return titles.stream()
                .map(ECFRTitle::getLastUpdated)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }

    public List<AgencyMetrics> getTopAgenciesByMetric(String metric, int limit) {
        Map<String, AgencyMetrics> allMetrics = generateAgencyMetrics(dataStorageService.loadTitles());

        Comparator<AgencyMetrics> comparator;
        switch (metric.toLowerCase()) {
            case "regulations":
                comparator = Comparator.comparing(AgencyMetrics::getTotalRegulations);
                break;
            case "words":
                comparator = Comparator.comparing(AgencyMetrics::getTotalWordCount);
                break;
            case "complexity":
                comparator = Comparator.comparing(AgencyMetrics::getRegulatoryComplexityIndex);
                break;
            default:
                comparator = Comparator.comparing(AgencyMetrics::getTotalRegulations);
        }

        return allMetrics.values().stream()
                .sorted(comparator.reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
