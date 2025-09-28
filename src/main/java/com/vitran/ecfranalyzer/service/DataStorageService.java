package com.vitran.ecfranalyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vitran.ecfranalyzer.model.ECFRTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataStorageService {

    private static final Logger logger = LoggerFactory.getLogger(DataStorageService.class);
    private static final String DATA_DIR = "data/processed"; // Changed from "ecfr-data" to align with new structure
    private static final String TITLES_FILE = "ecfr-titles.json";
    private static final String METADATA_FILE = "metadata.json";

    private final ObjectMapper objectMapper;
    private final Path dataDirectory;

    public DataStorageService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.dataDirectory = Paths.get(DATA_DIR);
        initializeDataDirectory();
    }

    private void initializeDataDirectory() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created data directory: {}", dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }

    public void saveTitles(List<ECFRTitle> titles) {
        try {
            Path titlesPath = dataDirectory.resolve(TITLES_FILE);
            objectMapper.writeValue(titlesPath.toFile(), titles);
            logger.info("Saved {} titles to {}", titles.size(), titlesPath);

            // Save metadata
            saveMetadata(titles.size(), LocalDateTime.now());

        } catch (IOException e) {
            logger.error("Failed to save titles", e);
        }
    }

    public List<ECFRTitle> loadTitles() {
        try {
            Path titlesPath = dataDirectory.resolve(TITLES_FILE);
            if (Files.exists(titlesPath)) {
                List<ECFRTitle> titles = objectMapper.readValue(titlesPath.toFile(),
                        new TypeReference<List<ECFRTitle>>() {});
                logger.info("Loaded {} titles from {}", titles.size(), titlesPath);
                return titles;
            }
        } catch (IOException e) {
            logger.error("Failed to load titles", e);
        }

        return new ArrayList<>();
    }

    public void saveMetadata(int totalTitles, LocalDateTime lastUpdate) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("totalTitles", totalTitles);
            metadata.put("lastUpdate", lastUpdate);
            metadata.put("version", "1.0");

            Path metadataPath = dataDirectory.resolve(METADATA_FILE);
            objectMapper.writeValue(metadataPath.toFile(), metadata);

        } catch (IOException e) {
            logger.error("Failed to save metadata", e);
        }
    }

    public Map<String, Object> loadMetadata() {
        try {
            Path metadataPath = dataDirectory.resolve(METADATA_FILE);
            if (Files.exists(metadataPath)) {
                return objectMapper.readValue(metadataPath.toFile(),
                        new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException e) {
            logger.error("Failed to load metadata", e);
        }

        return new HashMap<>();
    }

    public LocalDateTime getLastUpdateTime() {
        Map<String, Object> metadata = loadMetadata();
        Object lastUpdate = metadata.get("lastUpdate");
        if (lastUpdate != null) {
            try {
                // Handle Jackson's array format for LocalDateTime: [year, month, day, hour, minute, second, nano]
                if (lastUpdate instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Integer> dateArray = (List<Integer>) lastUpdate;
                    if (dateArray.size() >= 6) {
                        return LocalDateTime.of(
                            dateArray.get(0), // year
                            dateArray.get(1), // month
                            dateArray.get(2), // day
                            dateArray.get(3), // hour
                            dateArray.get(4), // minute
                            dateArray.get(5), // second
                            dateArray.size() > 6 ? dateArray.get(6) : 0 // nanoseconds (optional)
                        );
                    }
                } else {
                    // Fallback: try to parse as string
                    return LocalDateTime.parse(lastUpdate.toString());
                }
            } catch (Exception e) {
                logger.warn("Failed to parse last update time", e);
            }
        }
        return null;
    }

    public boolean hasExistingData() {
        Path titlesPath = dataDirectory.resolve(TITLES_FILE);
        return Files.exists(titlesPath) && !loadTitles().isEmpty();
    }

    public void saveAgencyData(String agency, List<ECFRTitle> agencyTitles) {
        try {
            String fileName = "agency-" + sanitizeFileName(agency) + ".json";
            Path agencyPath = dataDirectory.resolve(fileName);
            objectMapper.writeValue(agencyPath.toFile(), agencyTitles);
            logger.debug("Saved {} titles for agency {} to {}", agencyTitles.size(), agency, agencyPath);
        } catch (IOException e) {
            logger.error("Failed to save agency data for {}", agency, e);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9\\-_]", "_").toLowerCase();
    }
}
