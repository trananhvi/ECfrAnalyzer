package com.vitran.ecfranalyzer.model;

import java.time.LocalDateTime;

public class AgencyMetrics {

    private String agencyName;
    private int totalRegulations;
    private long totalWordCount;
    private String checksum;
    private LocalDateTime lastUpdated;
    private double averageWordsPerRegulation;
    private int uniqueTitles;
    private double regulatoryComplexityIndex; // Custom metric

    // Constructors
    public AgencyMetrics() {}

    public AgencyMetrics(String agencyName) {
        this.agencyName = agencyName;
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and Setters
    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }

    public int getTotalRegulations() { return totalRegulations; }
    public void setTotalRegulations(int totalRegulations) {
        this.totalRegulations = totalRegulations;
        calculateAverageWords();
    }

    public long getTotalWordCount() { return totalWordCount; }
    public void setTotalWordCount(long totalWordCount) {
        this.totalWordCount = totalWordCount;
        calculateAverageWords();
    }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public double getAverageWordsPerRegulation() { return averageWordsPerRegulation; }

    public int getUniqueTitles() { return uniqueTitles; }
    public void setUniqueTitles(int uniqueTitles) { this.uniqueTitles = uniqueTitles; }

    public double getRegulatoryComplexityIndex() { return regulatoryComplexityIndex; }
    public void setRegulatoryComplexityIndex(double regulatoryComplexityIndex) {
        this.regulatoryComplexityIndex = regulatoryComplexityIndex;
    }

    private void calculateAverageWords() {
        if (totalRegulations > 0) {
            this.averageWordsPerRegulation = (double) totalWordCount / totalRegulations;
        }
    }
}
