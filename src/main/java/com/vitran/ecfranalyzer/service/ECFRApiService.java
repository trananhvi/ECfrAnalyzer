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
import java.time.Duration;
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
    private static final int MAX_TITLES_TO_PROCESS = 10; // Limit for demo purposes

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ECFRApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<ECFRTitle> fetchAllTitles() {
        logger.info("Starting to fetch all eCFR titles from correct API endpoints");
        List<ECFRTitle> allTitles = new ArrayList<>();

        try {
            // Step 1: Fetch all agencies to map agencies to titles
            Map<String, ECFRAgency> agenciesMap = fetchAgencies();
            logger.info("Fetched {} agencies", agenciesMap.size());

            // Step 2: Fetch all available titles
            List<ECFRTitle> titles = fetchTitlesList();
            logger.info("Fetched {} titles", titles.size());

            // Step 3: Get current date for API calls
            String currentDate = LocalDateTime.now().format(dateFormatter);

            // Step 4: Enhance each title with structure and content (limited for demo)
            int processedCount = 0;
            for (ECFRTitle title : titles) {
                if (processedCount >= MAX_TITLES_TO_PROCESS) {
                    logger.info("Reached maximum titles limit for demo: {}", MAX_TITLES_TO_PROCESS);
                    break;
                }

                try {
                    enhanceTitleWithData(title, currentDate, agenciesMap);
                    allTitles.add(title);
                    processedCount++;

                    // Rate limiting
                    Thread.sleep(REQUEST_DELAY_MS);

                } catch (Exception e) {
                    logger.warn("Failed to enhance title {}: {}", title.getNumber(), e.getMessage());
                    // Add title even if enhancement fails
                    title.setLastUpdated(LocalDateTime.now());
                    title.setChecksum(generateChecksum(title));
                    allTitles.add(title);
                    processedCount++;
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching eCFR titles", e);
        }

        logger.info("Successfully processed {} eCFR titles", allTitles.size());
        return allTitles;
    }

    private Map<String, ECFRAgency> fetchAgencies() {
        Map<String, ECFRAgency> agenciesMap = new HashMap<>();

        try {
            String url = BASE_URL + AGENCIES_ENDPOINT;
            logger.info("Fetching agencies from: {}", url);

            HttpResponse<String> response = makeHttpRequest(url);
            if (response != null && response.statusCode() == 200) {
                // Use the correct wrapper model for agencies response
                ECFRAgenciesResponse agenciesResponse = objectMapper.readValue(response.body(), ECFRAgenciesResponse.class);

                if (agenciesResponse.getAgencies() != null) {
                    for (ECFRAgency agency : agenciesResponse.getAgencies()) {
                        agenciesMap.put(agency.getSlug(), agency);
                    }
                    logger.info("Successfully fetched {} agencies", agenciesResponse.getAgencies().size());
                } else {
                    logger.warn("No agencies found in API response");
                }
            } else {
                logger.warn("Failed to fetch agencies. Status: {}",
                           response != null ? response.statusCode() : "null");
            }

        } catch (Exception e) {
            logger.error("Error fetching agencies", e);
        }

        return agenciesMap;
    }

    private List<ECFRTitle> fetchTitlesList() {
        List<ECFRTitle> titles = new ArrayList<>();

        try {
            String url = BASE_URL + TITLES_ENDPOINT;
            logger.info("Fetching titles from: {}", url);

            HttpResponse<String> response = makeHttpRequest(url);
            if (response != null && response.statusCode() == 200) {
                // Parse the response which has a "titles" array structure
                ECFRApiResponse apiResponse = objectMapper.readValue(response.body(), ECFRApiResponse.class);

                if (apiResponse.getTitles() != null) {
                    titles = apiResponse.getTitles();
                    logger.info("Successfully fetched {} titles", titles.size());
                } else {
                    logger.warn("No titles found in API response");
                }
            } else {
                logger.warn("Failed to fetch titles. Status: {}",
                           response != null ? response.statusCode() : "null");
            }

        } catch (Exception e) {
            logger.error("Error fetching titles", e);
        }

        return titles;
    }

    private void enhanceTitleWithData(ECFRTitle title, String date, Map<String, ECFRAgency> agenciesMap) {
        try {
            // Skip reserved titles
            if (title.isReserved()) {
                logger.debug("Skipping reserved title: {}", title.getNumber());
                title.setAgency("Reserved");
                title.setLastUpdated(LocalDateTime.now());
                title.setChecksum(generateChecksum(title));
                return;
            }

            // Set agency based on title number - fix: convert int to String
            String agencyName = determineAgencyFromTitleNumber(String.valueOf(title.getNumber()));
            title.setAgency(agencyName);

            // Try to fetch structure data (may fail for some titles)
            try {
                String structureUrl = BASE_URL + STRUCTURE_ENDPOINT
                        .replace("{date}", date)
                        .replace("{title}", String.valueOf(title.getNumber()));

                logger.debug("Fetching structure for title {}: {}", title.getNumber(), structureUrl);
                HttpResponse<String> structureResponse = makeHttpRequest(structureUrl);

                if (structureResponse != null && structureResponse.statusCode() == 200) {
                    title.setStructureData(structureResponse.body());

                    // Extract word count from structure if available
                    int estimatedWords = estimateWordsFromStructure(structureResponse.body());
                    title.setWordCount(estimatedWords);
                } else {
                    logger.debug("Structure not available for title {}", title.getNumber());
                    // Set a default word count based on title complexity
                    title.setWordCount(estimateWordsFromTitleName(title.getName()));
                }
            } catch (Exception e) {
                logger.debug("Could not fetch structure for title {}: {}", title.getNumber(), e.getMessage());
                title.setWordCount(estimateWordsFromTitleName(title.getName()));
            }

            // Set basic content from title name for now
            title.setContent("CFR Title " + title.getNumber() + ": " + title.getName() +
                           " - Last amended: " + title.getLatestAmendedOn() +
                           " - Issue date: " + title.getLatestIssueDate());

            // Set metadata
            title.setLastUpdated(LocalDateTime.now());
            title.setChecksum(generateChecksum(title));

        } catch (Exception e) {
            logger.warn("Failed to enhance title {}: {}", title.getNumber(), e.getMessage());
            throw e;
        }
    }

    private int estimateWordsFromStructure(String structureJson) {
        // Simple estimation based on JSON size and complexity
        if (structureJson == null) return 1000;

        // Count occurrences of common regulation keywords to estimate content
        int keywordCount = 0;
        String[] keywords = {"section", "part", "chapter", "subpart", "paragraph", "regulation", "rule"};
        String lowerJson = structureJson.toLowerCase();

        for (String keyword : keywords) {
            keywordCount += (lowerJson.length() - lowerJson.replace(keyword, "").length()) / keyword.length();
        }

        // Estimate words based on structure complexity
        return Math.max(1000, keywordCount * 50);
    }

    private int estimateWordsFromTitleName(String titleName) {
        // Simple estimation: longer, more complex titles likely have more regulations
        if (titleName == null) return 1000;

        int baseWords = 5000; // Base estimate
        int nameLength = titleName.length();

        // Adjust based on title name complexity
        if (nameLength > 30) baseWords += 2000;
        if (titleName.toLowerCase().contains("administration")) baseWords += 3000;
        if (titleName.toLowerCase().contains("management")) baseWords += 2000;
        if (titleName.toLowerCase().contains("regulation")) baseWords += 4000;

        return baseWords;
    }

    private void extractAgencyFromStructure(ECFRTitle title, String structureJson, Map<String, ECFRAgency> agenciesMap) {
        try {
            // Simple approach: try to find agency info in the structure JSON
            // In a real implementation, you'd parse the JSON structure properly
            if (structureJson.contains("\"agency\"")) {
                // For now, assign based on title number patterns or use a default - fix: convert int to String
                String agencyName = determineAgencyFromTitleNumber(String.valueOf(title.getNumber()));
                title.setAgency(agencyName);
            }
        } catch (Exception e) {
            logger.debug("Could not extract agency from structure for title {}", title.getNumber());
        }
    }

    private String determineAgencyFromTitleNumber(String titleNumber) {
        // Simple mapping based on common CFR title patterns
        // This is a simplified approach - in practice you'd use the actual structure data
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
        // Simple text extraction from XML - remove tags
        if (xmlContent == null) return "";

        // Remove XML tags and clean up
        String text = xmlContent.replaceAll("<[^>]+>", " ")
                                .replaceAll("\\s+", " ")
                                .trim();

        // Limit text length for demo purposes
        return text.length() > 10000 ? text.substring(0, 10000) + "..." : text;
    }

    private HttpResponse<String> makeHttpRequest(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "eCFR-Analyzer/1.0")
                    .GET()
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (IOException | InterruptedException e) {
            logger.error("Error making HTTP request to: {}", url, e);
            return null;
        }
    }

    public List<ECFRTitle> fetchUpdatedTitles(LocalDateTime since) {
        logger.info("Fetching eCFR titles updated since: {}", since);
        // For simplicity, fetch all titles - in production you'd implement proper incremental logic
        return fetchAllTitles();
    }

    private String generateChecksum(ECFRTitle title) {
        String content = String.valueOf(title.getNumber()) +
                        (title.getName() != null ? title.getName() : "") +
                        (title.getContent() != null ? title.getContent() : "");
        return String.valueOf(content.hashCode());
    }
}
