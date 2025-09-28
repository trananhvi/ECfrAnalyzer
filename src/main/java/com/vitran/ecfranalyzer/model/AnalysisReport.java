package com.vitran.ecfranalyzer.model;

import java.time.LocalDateTime;
import java.util.Map;

public class AnalysisReport {

    private LocalDateTime generatedAt;
    private int totalRegulations;
    private long totalWordCount;
    private int totalAgencies;
    private Map<String, AgencyMetrics> agencyMetrics;
    private String overallChecksum;
    private LocalDateTime lastDataUpdate;

    // Top agencies by various metrics
    private String mostRegulationsAgency;
    private String mostWordsAgency;
    private String highestComplexityAgency;

    // Constructors
    public AnalysisReport() {
        this.generatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public int getTotalRegulations() { return totalRegulations; }
    public void setTotalRegulations(int totalRegulations) { this.totalRegulations = totalRegulations; }

    public long getTotalWordCount() { return totalWordCount; }
    public void setTotalWordCount(long totalWordCount) { this.totalWordCount = totalWordCount; }

    public int getTotalAgencies() { return totalAgencies; }
    public void setTotalAgencies(int totalAgencies) { this.totalAgencies = totalAgencies; }

    public Map<String, AgencyMetrics> getAgencyMetrics() { return agencyMetrics; }
    public void setAgencyMetrics(Map<String, AgencyMetrics> agencyMetrics) { this.agencyMetrics = agencyMetrics; }

    public String getOverallChecksum() { return overallChecksum; }
    public void setOverallChecksum(String overallChecksum) { this.overallChecksum = overallChecksum; }

    public LocalDateTime getLastDataUpdate() { return lastDataUpdate; }
    public void setLastDataUpdate(LocalDateTime lastDataUpdate) { this.lastDataUpdate = lastDataUpdate; }

    public String getMostRegulationsAgency() { return mostRegulationsAgency; }
    public void setMostRegulationsAgency(String mostRegulationsAgency) { this.mostRegulationsAgency = mostRegulationsAgency; }

    public String getMostWordsAgency() { return mostWordsAgency; }
    public void setMostWordsAgency(String mostWordsAgency) { this.mostWordsAgency = mostWordsAgency; }

    public String getHighestComplexityAgency() { return highestComplexityAgency; }
    public void setHighestComplexityAgency(String highestComplexityAgency) { this.highestComplexityAgency = highestComplexityAgency; }
}
