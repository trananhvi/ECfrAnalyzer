package com.vitran.ecfranalyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitran.ecfranalyzer.model.ECFRAgency;
import com.vitran.ecfranalyzer.model.ECFRAgenciesResponse;
import com.vitran.ecfranalyzer.model.ECFRApiResponse;
import com.vitran.ecfranalyzer.model.ECFRTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ECFRApiService {

    private static final Logger logger = LoggerFactory.getLogger(ECFRApiService.class);
    private static final String BASE_URL = "https://www.ecfr.gov";
    private static final String AGENCIES_ENDPOINT = "/api/admin/v1/agencies.json";
    private static final String TITLES_ENDPOINT = "/api/versioner/v1/titles.json";
    private static final String STRUCTURE_ENDPOINT = "/api/versioner/v1/structure/{date}/title-{title}.json";
    private static final String CONTENT_ENDPOINT = "/api/versioner/v1/full/{date}/title-{title}.xml";
    private static final int REQUEST_DELAY_MS = 1000; // Rate limiting
    private static final int MAX_TITLES_TO_PROCESS = 50; // Limit for demo purposes
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Enhanced directory structure
    private static final String DATA_DIR = "data";
    private static final String RAW_DIR = "data/raw";
    private static final String PROCESSED_DIR = "data/processed";
    private static final String CACHE_DIR = "data/cache";
    private static final String STATE_DIR = "data/state";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ECFRApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        initializeDirectoryStructure();
    }

    private void initializeDirectoryStructure() {
        try {
            // Create directory structure
            Files.createDirectories(Paths.get(RAW_DIR));
            Files.createDirectories(Paths.get(PROCESSED_DIR));
            Files.createDirectories(Paths.get(CACHE_DIR + "/xml-parsing"));
            Files.createDirectories(Paths.get(STATE_DIR));

            logger.info("Initialized data directory structure");
        } catch (IOException e) {
            logger.error("Failed to create directory structure", e);
        }
    }

    public List<ECFRTitle> fetchAllTitles() {
        logger.info("Starting comprehensive eCFR data acquisition pipeline");

        try {
            // PHASE 1: RAW DATA ACQUISITION

            // Step 1: Get All Agencies
            Map<String, ECFRAgency> agenciesMap = fetchAndSaveAgencies();
            logger.info("Step 1 complete: Agencies data saved to raw/agencies.json");

            // Step 2: Get All Titles Metadata
            List<ECFRTitle> allTitles = fetchAndSaveTitles();
            logger.info("Step 2 complete: Titles metadata saved to raw/titles.json");

            // Step 3 & 4: Get Structure and Content for each title
            List<ECFRTitle> enhancedTitles = processAllTitles(allTitles, agenciesMap);
            logger.info("Steps 3 & 4 complete: Structure and content data saved for {} titles", enhancedTitles.size());

            // Update processing state
            updateProcessingState(enhancedTitles.size());

            return enhancedTitles;

        } catch (Exception e) {
            logger.error("Error in comprehensive data acquisition pipeline", e);
            return new ArrayList<>();
        }
    }

    private Map<String, ECFRAgency> fetchAndSaveAgencies() {
        Map<String, ECFRAgency> agenciesMap = new HashMap<>();

        try {
            String url = BASE_URL + AGENCIES_ENDPOINT;
            logger.info("Fetching agencies from: {}", url);

            HttpResponse<String> response = makeHttpRequestWithRetry(url);
            if (response != null && response.statusCode() == 200) {

                // Save raw agencies data
                Path agenciesPath = Paths.get(RAW_DIR, "agencies.json");
                Files.writeString(agenciesPath, response.body());
                logger.info("Saved raw agencies data to: {}", agenciesPath);

                // Parse and build agencies map
                ECFRAgenciesResponse agenciesResponse = objectMapper.readValue(response.body(), ECFRAgenciesResponse.class);

                if (agenciesResponse.getAgencies() != null) {
                    for (ECFRAgency agency : agenciesResponse.getAgencies()) {
                        agenciesMap.put(agency.getSlug(), agency);
                    }
                    logger.info("Processed {} agencies", agenciesResponse.getAgencies().size());
                }
            } else {
                logger.warn("Failed to fetch agencies. Status: {}", response != null ? response.statusCode() : "null");
            }

        } catch (Exception e) {
            logger.error("Error fetching agencies", e);
        }

        return agenciesMap;
    }

    private List<ECFRTitle> fetchAndSaveTitles() {
        List<ECFRTitle> titles = new ArrayList<>();

        try {
            String url = BASE_URL + TITLES_ENDPOINT;
            logger.info("Fetching all titles from: {}", url);

            HttpResponse<String> response = makeHttpRequestWithRetry(url);
            if (response != null && response.statusCode() == 200) {

                // Save raw titles data
                Path titlesPath = Paths.get(RAW_DIR, "titles.json");
                Files.writeString(titlesPath, response.body());
                logger.info("Saved raw titles data to: {}", titlesPath);

                // Parse titles
                ECFRApiResponse apiResponse = objectMapper.readValue(response.body(), ECFRApiResponse.class);

                if (apiResponse.getTitles() != null) {
                    titles = apiResponse.getTitles();
                    logger.info("Retrieved all {} CFR titles from eCFR API", titles.size());
                }
            } else {
                logger.warn("Failed to fetch titles. Status: {}", response != null ? response.statusCode() : "null");
            }

        } catch (Exception e) {
            logger.error("Error fetching titles", e);
        }

        return titles;
    }

    private List<ECFRTitle> processAllTitles(List<ECFRTitle> allTitles, Map<String, ECFRAgency> agenciesMap) {
        List<ECFRTitle> processedTitles = new ArrayList<>();
        int processedCount = 0;

        for (ECFRTitle title : allTitles) {
            if (processedCount >= MAX_TITLES_TO_PROCESS) {
                logger.info("Reached maximum titles limit for demo: {}", MAX_TITLES_TO_PROCESS);
                break;
            }

            try {
                if (title.isReserved()) {
                    logger.debug("Skipping reserved title: {}", title.getNumber());
                    continue;
                }

                logger.info("Processing title {} - {}", title.getNumber(), title.getName());

                // Set agency mapping
                String agencyName = determineAgencyFromTitleNumber(String.valueOf(title.getNumber()));
                title.setAgency(agencyName);

                // Use title's latest_issue_date for API calls
                String titleDate = normalizeDate(title.getLatestIssueDate());
                if (titleDate == null) {
                    titleDate = LocalDateTime.now().format(dateFormatter);
                }

                // Step 3: Fetch and save structure data
                fetchAndSaveStructureData(title, titleDate);

                // Step 4: Fetch and save XML content
                fetchAndSaveXmlContent(title, titleDate);

                // Set metadata
                title.setLastUpdated(LocalDateTime.now());
                title.setChecksum(generateChecksum(title));

                processedTitles.add(title);
                processedCount++;

                // Rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

            } catch (Exception e) {
                logger.warn("Failed to process title {}: {}", title.getNumber(), e.getMessage());
                // Continue with next title on error
            }
        }

        return processedTitles;
    }

    private void fetchAndSaveStructureData(ECFRTitle title, String date) {
        try {
            String structureUrl = BASE_URL + STRUCTURE_ENDPOINT
                    .replace("{date}", date)
                    .replace("{title}", String.valueOf(title.getNumber()));

            logger.debug("Fetching structure for title {}: {}", title.getNumber(), structureUrl);
            HttpResponse<String> response = makeHttpRequestWithRetry(structureUrl);

            if (response != null && response.statusCode() == 200) {
                // Save raw structure data
                Path structurePath = Paths.get(RAW_DIR, String.format("title-%d-structure.json", title.getNumber()));
                Files.writeString(structurePath, response.body());

                title.setStructureData(response.body());
                logger.debug("Saved structure data for title {} to: {}", title.getNumber(), structurePath);
            } else {
                logger.debug("Structure not available for title {} (HTTP: {})",
                           title.getNumber(), response != null ? response.statusCode() : "null");
            }

        } catch (Exception e) {
            logger.debug("Could not fetch structure for title {}: {}", title.getNumber(), e.getMessage());
        }
    }

    private void fetchAndSaveXmlContent(ECFRTitle title, String primaryDate) {
        String[] datesToTry = {
            primaryDate,
            title.getUpToDateAsOf(),
            title.getLatestAmendedOn(),
            "2024-01-01" // Fallback
        };

        for (String tryDate : datesToTry) {
            if (tryDate == null || tryDate.trim().isEmpty()) continue;

            String normalizedDate = normalizeDate(tryDate);
            if (normalizedDate == null) continue;

            try {
                String contentUrl = BASE_URL + CONTENT_ENDPOINT
                        .replace("{date}", normalizedDate)
                        .replace("{title}", String.valueOf(title.getNumber()));

                logger.debug("Fetching XML content for title {} with date {}", title.getNumber(), normalizedDate);

                // Add delay for rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

                HttpResponse<String> response = makeHttpRequestWithRetry(contentUrl);

                if (response != null && response.statusCode() == 200) {
                    String xmlContent = response.body();

                    // Validate XML content
                    if (xmlContent != null && xmlContent.trim().length() > 100 &&
                        (xmlContent.contains("<CFR>") || xmlContent.contains("<?xml"))) {

                        // Save raw XML data
                        Path xmlPath = Paths.get(RAW_DIR, String.format("title-%d.xml", title.getNumber()));
                        Files.writeString(xmlPath, xmlContent);

                        // Extract and set processed content
                        String textContent = extractTextFromXml(xmlContent);
                        int wordCount = countWords(textContent);

                        title.setContent(textContent);
                        title.setWordCount(wordCount);

                        logger.info("Saved XML content for title {} using date {}: {} words to {}",
                                  title.getNumber(), normalizedDate, wordCount, xmlPath);
                        return; // Success, no need to try other dates
                    }
                }

            } catch (Exception e) {
                logger.debug("Failed to fetch XML for title {} with date {}: {}",
                           title.getNumber(), normalizedDate, e.getMessage());
            }
        }

        // If no XML content found, set fallback
        logger.info("No XML content available for title {} - using metadata fallback", title.getNumber());
        setFallbackContent(title);
    }

    private HttpResponse<String> makeHttpRequestWithRetry(String url) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("User-Agent", "eCFR-Analyzer/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response;
                } else if (attempt < MAX_RETRY_ATTEMPTS && response.statusCode() >= 500) {
                    // Retry on server errors
                    logger.debug("Server error {} for {}, retrying attempt {}", response.statusCode(), url, attempt + 1);
                    Thread.sleep(REQUEST_DELAY_MS * attempt); // Exponential backoff
                    continue;
                }

                return response; // Return even on client errors (4xx) - don't retry

            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.debug("Request failed for {}, retrying attempt {}: {}", url, attempt + 1, e.getMessage());
                    try {
                        Thread.sleep(REQUEST_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.error("All retry attempts failed for: {}", url, lastException);
        return null;
    }

    private void updateProcessingState(int processedTitles) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("lastRun", LocalDateTime.now().toString());
            state.put("processedTitles", processedTitles);
            state.put("status", "completed");

            Path statePath = Paths.get(STATE_DIR, "last-run.json");
            objectMapper.writeValue(statePath.toFile(), state);

            logger.info("Updated processing state: {} titles processed", processedTitles);

        } catch (Exception e) {
            logger.error("Failed to update processing state", e);
        }
    }

    private String determineAgencyFromTitleNumber(String titleNumber) {
        try {
            int titleNum = Integer.parseInt(titleNumber);
            switch (titleNum) {
                case 1: return "General Provisions";
                case 2: return "Grants and Agreements";
                case 3: return "The President";
                case 4: return "Accounts";
                case 5: return "Administrative Personnel";
                case 7: return "Agriculture";
                case 8: return "Aliens and Nationality";
                case 9: return "Animals and Animal Products";
                case 10: return "Energy";
                case 12: return "Banks and Banking";
                case 14: return "Aeronautics and Space";
                case 15: return "Commerce and Foreign Trade";
                case 16: return "Commercial Practices";
                case 17: return "Commodity and Securities Exchanges";
                case 18: return "Conservation of Power and Water Resources";
                case 19: return "Customs Duties";
                case 20: return "Employees' Benefits";
                case 21: return "Food and Drugs";
                case 22: return "Foreign Relations";
                case 24: return "Housing and Urban Development";
                case 25: return "Indians";
                case 26: return "Internal Revenue";
                case 27: return "Alcohol, Tobacco Products and Firearms";
                case 28: return "Judicial Administration";
                case 29: return "Labor";
                case 30: return "Mineral Resources";
                case 32: return "National Defense";
                case 33: return "Navigation and Navigable Waters";
                case 34: return "Education";
                case 36: return "Parks, Forests, and Public Property";
                case 38: return "Pensions, Bonuses, and Veterans' Relief";
                case 40: return "Protection of Environment";
                case 41: return "Public Contracts and Property Management";
                case 42: return "Public Health";
                case 43: return "Public Lands: Interior";
                case 44: return "Emergency Management and Assistance";
                case 45: return "Public Welfare";
                case 46: return "Shipping";
                case 47: return "Telecommunication";
                case 48: return "Federal Acquisition Regulations System";
                case 49: return "Transportation";
                case 50: return "Wildlife and Fisheries";
                default: return "Federal Agency (Title " + titleNumber + ")";
            }
        } catch (NumberFormatException e) {
            return "Unknown Agency";
        }
    }

    private String extractTextFromXml(String xmlContent) {
        if (xmlContent == null) return "";

        String text = xmlContent.replaceAll("<[^>]+>", " ")
                                .replaceAll("\\s+", " ")
                                .trim();

        return text.length() > 10000 ? text.substring(0, 10000) + "..." : text;
    }

    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                return dateStr;
            }

            if (dateStr.matches("\\d{4}/\\d{2}/\\d{2}")) {
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            }

            LocalDate date = LocalDate.parse(dateStr);
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);

        } catch (Exception e) {
            logger.debug("Could not normalize date: {} - {}", dateStr, e.getMessage());
            return null;
        }
    }

    private void setFallbackContent(ECFRTitle title) {
        int wordCount = title.getStructureData() != null ?
                       estimateWordsFromStructure(title.getStructureData()) :
                       estimateWordsFromTitleName(title.getName());

        title.setContent("CFR Title " + title.getNumber() + ": " + title.getName() +
                         " - Last amended: " + title.getLatestAmendedOn() +
                         " - Issue date: " + title.getLatestIssueDate());
        title.setWordCount(wordCount);
    }

    private int estimateWordsFromStructure(String structureJson) {
        if (structureJson == null) return 1000;

        String[] keywords = {"section", "part", "chapter", "subpart", "paragraph", "regulation", "rule"};
        String lowerJson = structureJson.toLowerCase();
        int keywordCount = 0;

        for (String keyword : keywords) {
            keywordCount += (lowerJson.length() - lowerJson.replace(keyword, "").length()) / keyword.length();
        }

        return Math.max(1000, keywordCount * 50);
    }

    private int estimateWordsFromTitleName(String titleName) {
        if (titleName == null) return 1000;

        int baseWords = 5000;
        int nameLength = titleName.length();

        if (nameLength > 30) baseWords += 2000;
        if (titleName.toLowerCase().contains("administration")) baseWords += 3000;
        if (titleName.toLowerCase().contains("management")) baseWords += 2000;
        if (titleName.toLowerCase().contains("regulation")) baseWords += 4000;

        return baseWords;
    }

    private String generateChecksum(ECFRTitle title) {
        String content = String.valueOf(title.getNumber()) +
                        (title.getName() != null ? title.getName() : "") +
                        (title.getContent() != null ? title.getContent() : "");
        return String.valueOf(content.hashCode());
    }

    public List<ECFRTitle> fetchUpdatedTitles(LocalDateTime since) {
        logger.info("Fetching eCFR titles updated since: {}", since);
        // For now, perform full refresh - incremental logic can be added later
        return fetchAllTitles();
    }
}
