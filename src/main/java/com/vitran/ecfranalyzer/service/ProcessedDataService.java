package com.vitran.ecfranalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitran.ecfranalyzer.model.ECFRTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 2: ANALYTICS PROCESSING
 * Processes raw eCFR data and generates analytics files in data/processed/
 */
@Service
public class ProcessedDataService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedDataService.class);
    private static final String PROCESSED_DIR = "data/processed";

    @Autowired
    private DataStorageService dataStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate all processed analytics files from raw data
     */
    public void generateProcessedAnalytics() {
        logger.info("Starting PHASE 2: Analytics Processing Pipeline");

        try {
            // Load raw data
            List<ECFRTitle> allTitles = dataStorageService.loadTitles();
            if (allTitles.isEmpty()) {
                logger.warn("No titles found - run data acquisition first");
                return;
            }

            // Generate all processed analytics files
            generateWordCounts(allTitles);
            generateChecksums(allTitles);
            generateHistoricalChanges(allTitles);
            generateComplexityScores(allTitles);
            generateAgencyMetrics(allTitles);
            generateTitleSummaries(allTitles);

            logger.info("Analytics processing complete - all files saved to {}", PROCESSED_DIR);

        } catch (Exception e) {
            logger.error("Error in analytics processing pipeline", e);
        }
    }

    /**
     * Generate word-counts.json
     * Agency name mapped to total word count across all their regulations
     */
    private void generateWordCounts(List<ECFRTitle> titles) {
        try {
            Map<String, Long> wordCounts = titles.stream()
                .filter(title -> title.getAgency() != null)
                .collect(Collectors.groupingBy(
                    ECFRTitle::getAgency,
                    Collectors.summingLong(ECFRTitle::getWordCount)
                ));

            Path wordCountsPath = Paths.get(PROCESSED_DIR, "word-counts.json");
            objectMapper.writeValue(wordCountsPath.toFile(), wordCounts);

            logger.info("Generated word-counts.json with {} agencies", wordCounts.size());

        } catch (Exception e) {
            logger.error("Failed to generate word counts", e);
        }
    }

    /**
     * Generate checksums.json
     * Agency name mapped to SHA256 hash of their complete regulatory content
     */
    private void generateChecksums(List<ECFRTitle> titles) {
        try {
            Map<String, String> agencyChecksums = new HashMap<>();

            // Group titles by agency
            Map<String, List<ECFRTitle>> agencyGroups = titles.stream()
                .filter(title -> title.getAgency() != null)
                .collect(Collectors.groupingBy(ECFRTitle::getAgency));

            // Generate SHA256 checksum for each agency
            for (Map.Entry<String, List<ECFRTitle>> entry : agencyGroups.entrySet()) {
                String agency = entry.getKey();
                List<ECFRTitle> agencyTitles = entry.getValue();

                String checksum = generateSHA256Checksum(agencyTitles);
                agencyChecksums.put(agency, checksum);
            }

            Path checksumsPath = Paths.get(PROCESSED_DIR, "checksums.json");
            objectMapper.writeValue(checksumsPath.toFile(), agencyChecksums);

            logger.info("Generated checksums.json with {} agency checksums", agencyChecksums.size());

        } catch (Exception e) {
            logger.error("Failed to generate checksums", e);
        }
    }

    /**
     * Generate historical-changes.json
     * Array of change objects tracking amendments, additions, removals over time
     */
    private void generateHistoricalChanges(List<ECFRTitle> titles) {
        try {
            List<Map<String, Object>> changes = new ArrayList<>();

            for (ECFRTitle title : titles) {
                if (title.getLatestAmendedOn() != null) {
                    Map<String, Object> change = new HashMap<>();
                    change.put("agency", title.getAgency());
                    change.put("title", title.getNumber());
                    change.put("titleName", title.getName());
                    change.put("date", title.getLatestAmendedOn());
                    change.put("issueDate", title.getLatestIssueDate());
                    change.put("upToDate", title.getUpToDateAsOf());
                    change.put("type", "amendment");
                    change.put("description", "Title " + title.getNumber() + " amended");
                    change.put("wordCount", title.getWordCount());

                    changes.add(change);
                }
            }

            // Sort by date (most recent first)
            changes.sort((a, b) -> {
                String dateA = (String) a.get("date");
                String dateB = (String) b.get("date");
                return dateB.compareTo(dateA);
            });

            Path changesPath = Paths.get(PROCESSED_DIR, "historical-changes.json");
            objectMapper.writeValue(changesPath.toFile(), changes);

            logger.info("Generated historical-changes.json with {} change records", changes.size());

        } catch (Exception e) {
            logger.error("Failed to generate historical changes", e);
        }
    }

    /**
     * Generate complexity-scores.json
     * Custom metric calculating regulatory complexity per agency (1-10 scale)
     */
    private void generateComplexityScores(List<ECFRTitle> titles) {
        try {
            Map<String, Double> complexityScores = new HashMap<>();

            // Group titles by agency
            Map<String, List<ECFRTitle>> agencyGroups = titles.stream()
                .filter(title -> title.getAgency() != null)
                .collect(Collectors.groupingBy(ECFRTitle::getAgency));

            for (Map.Entry<String, List<ECFRTitle>> entry : agencyGroups.entrySet()) {
                String agency = entry.getKey();
                List<ECFRTitle> agencyTitles = entry.getValue();

                double complexity = calculateComplexityScore(agencyTitles);
                complexityScores.put(agency, complexity);
            }

            Path complexityPath = Paths.get(PROCESSED_DIR, "complexity-scores.json");
            objectMapper.writeValue(complexityPath.toFile(), complexityScores);

            logger.info("Generated complexity-scores.json with {} agency scores", complexityScores.size());

        } catch (Exception e) {
            logger.error("Failed to generate complexity scores", e);
        }
    }

    /**
     * Generate agency-metrics.json
     * Consolidated statistics per agency including total words, sections, parts, etc.
     */
    private void generateAgencyMetrics(List<ECFRTitle> titles) {
        try {
            Map<String, Map<String, Object>> agencyMetrics = new HashMap<>();

            // Group titles by agency
            Map<String, List<ECFRTitle>> agencyGroups = titles.stream()
                .filter(title -> title.getAgency() != null)
                .collect(Collectors.groupingBy(ECFRTitle::getAgency));

            for (Map.Entry<String, List<ECFRTitle>> entry : agencyGroups.entrySet()) {
                String agency = entry.getKey();
                List<ECFRTitle> agencyTitles = entry.getValue();

                Map<String, Object> metrics = new HashMap<>();
                metrics.put("agencyName", agency);
                metrics.put("totalWords", agencyTitles.stream().mapToLong(ECFRTitle::getWordCount).sum());
                metrics.put("totalRegulations", agencyTitles.size());
                metrics.put("cfrTitles", agencyTitles.stream().map(ECFRTitle::getNumber).collect(Collectors.toSet()));
                metrics.put("checksum", generateSHA256Checksum(agencyTitles));
                metrics.put("complexityScore", calculateComplexityScore(agencyTitles));
                metrics.put("lastUpdated", LocalDateTime.now().toString());

                // Calculate latest amendment date
                String latestAmendment = agencyTitles.stream()
                    .map(ECFRTitle::getLatestAmendedOn)
                    .filter(Objects::nonNull)
                    .max(String::compareTo)
                    .orElse("Unknown");
                metrics.put("latestAmendment", latestAmendment);

                agencyMetrics.put(agency, metrics);
            }

            Path metricsPath = Paths.get(PROCESSED_DIR, "agency-metrics.json");
            objectMapper.writeValue(metricsPath.toFile(), agencyMetrics);

            logger.info("Generated agency-metrics.json with {} agencies", agencyMetrics.size());

        } catch (Exception e) {
            logger.error("Failed to generate agency metrics", e);
        }
    }

    /**
     * Generate title-summaries.json
     * Per-title analysis including word count, section count, last amendment date, responsible agencies
     */
    private void generateTitleSummaries(List<ECFRTitle> titles) {
        try {
            Map<Integer, Map<String, Object>> titleSummaries = new HashMap<>();

            for (ECFRTitle title : titles) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("number", title.getNumber());
                summary.put("name", title.getName());
                summary.put("agency", title.getAgency());
                summary.put("wordCount", title.getWordCount());
                summary.put("reserved", title.isReserved());
                summary.put("latestAmendedOn", title.getLatestAmendedOn());
                summary.put("latestIssueDate", title.getLatestIssueDate());
                summary.put("upToDateAsOf", title.getUpToDateAsOf());
                summary.put("checksum", title.getChecksum());
                summary.put("lastUpdated", title.getLastUpdated() != null ? title.getLastUpdated().toString() : null);

                // Estimate sections from structure data if available
                int estimatedSections = estimateSectionsFromStructure(title.getStructureData());
                summary.put("estimatedSections", estimatedSections);

                titleSummaries.put(title.getNumber(), summary);
            }

            Path summariesPath = Paths.get(PROCESSED_DIR, "title-summaries.json");
            objectMapper.writeValue(summariesPath.toFile(), titleSummaries);

            logger.info("Generated title-summaries.json with {} titles", titleSummaries.size());

        } catch (Exception e) {
            logger.error("Failed to generate title summaries", e);
        }
    }

    /**
     * Generate SHA256 checksum for agency's complete regulatory content
     */
    private String generateSHA256Checksum(List<ECFRTitle> titles) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            String content = titles.stream()
                .sorted(Comparator.comparing(ECFRTitle::getNumber))
                .map(title -> title.getNumber() + "|" +
                             (title.getName() != null ? title.getName() : "") + "|" +
                             (title.getContent() != null ? title.getContent() : "") + "|" +
                             title.getWordCount())
                .collect(Collectors.joining("\n"));

            byte[] hash = md.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            logger.warn("Failed to generate SHA256 checksum", e);
            return String.valueOf(titles.hashCode());
        }
    }

    /**
     * Calculate regulatory complexity score (1-10 scale)
     * Factors: average section length, cross-references, legal term density, update frequency
     */
    private double calculateComplexityScore(List<ECFRTitle> titles) {
        if (titles.isEmpty()) return 1.0;

        // Factor 1: Verbosity (average words per regulation)
        double avgWords = titles.stream().mapToDouble(ECFRTitle::getWordCount).average().orElse(1000.0);
        double verbosityScore = Math.min(avgWords / 5000.0, 3.0); // Max 3 points

        // Factor 2: Scope (number of CFR titles covered)
        long uniqueTitles = titles.stream().map(ECFRTitle::getNumber).distinct().count();
        double scopeScore = Math.min(uniqueTitles / 5.0, 3.0); // Max 3 points

        // Factor 3: Update frequency (more recent updates = higher complexity)
        long recentUpdates = titles.stream()
            .filter(title -> title.getLatestAmendedOn() != null)
            .filter(title -> title.getLatestAmendedOn().compareTo("2024-01-01") >= 0)
            .count();
        double updateScore = Math.min((double) recentUpdates / titles.size() * 4.0, 4.0); // Max 4 points

        double totalScore = verbosityScore + scopeScore + updateScore;
        return Math.round(Math.min(totalScore, 10.0) * 100.0) / 100.0; // Round to 2 decimal places, max 10
    }

    /**
     * Estimate number of sections from structure data
     */
    private int estimateSectionsFromStructure(String structureData) {
        if (structureData == null) return 10; // Default estimate

        // Count occurrences of section-like patterns
        String[] sectionKeywords = {"section", "§", "sec.", "part", "subpart"};
        int sectionCount = 0;
        String lowerStructure = structureData.toLowerCase();

        for (String keyword : sectionKeywords) {
            sectionCount += (lowerStructure.length() - lowerStructure.replace(keyword, "").length()) / keyword.length();
        }

        return Math.max(sectionCount, 1);
    }
}
