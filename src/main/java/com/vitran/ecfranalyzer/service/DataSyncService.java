package com.vitran.ecfranalyzer.service;

import com.vitran.ecfranalyzer.model.ECFRTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@EnableScheduling
@EnableAsync
public class DataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DataSyncService.class);

    @Autowired
    private ECFRApiService ecfrApiService;

    @Autowired
    private DataStorageService dataStorageService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ProcessedDataService processedDataService;

    private volatile boolean isSyncing = false;
    private LocalDateTime lastSyncTime;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        logger.info("=== Starting Complete eCFR Data Processing Pipeline ===");

        if (isSyncing) {
            logger.warn("Sync already in progress - skipping initialization");
            return;
        }

        try {
            isSyncing = true;

            // PHASE 1: RAW DATA ACQUISITION
            logger.info("Phase 1: Raw Data Acquisition");
            performDataAcquisition();

            // PHASE 2: ANALYTICS PROCESSING
            logger.info("Phase 2: Analytics Processing");
            processedDataService.generateProcessedAnalytics();

            lastSyncTime = LocalDateTime.now();
            logger.info("=== Complete Pipeline Finished Successfully ===");
            logger.info("Data stored in:");
            logger.info("  - Raw data: data/raw/");
            logger.info("  - Analytics: data/processed/");
            logger.info("  - State: data/state/");

        } catch (Exception e) {
            logger.error("Error in complete data processing pipeline", e);
        } finally {
            isSyncing = false;
        }
    }

    /**
     * Perform data acquisition without async complexity
     */
    private void performDataAcquisition() {
        try {
            logger.info("Starting data acquisition");

            // Check if we have existing data
            boolean hasExistingData = dataStorageService.hasExistingData();

            List<ECFRTitle> updatedTitles;
            if (hasExistingData) {
                // Get last update time and fetch only updated titles
                LocalDateTime lastUpdate = dataStorageService.getLastUpdateTime();
                logger.info("Performing incremental sync since: {}", lastUpdate);
                updatedTitles = ecfrApiService.fetchUpdatedTitles(lastUpdate);
            } else {
                // First time - fetch all titles
                logger.info("No existing data found. Performing initial full sync");
                updatedTitles = ecfrApiService.fetchAllTitles();
            }

            if (!updatedTitles.isEmpty()) {
                // Save the updated/new titles
                dataStorageService.saveTitles(updatedTitles);
                logger.info("Successfully acquired {} titles", updatedTitles.size());
            } else {
                logger.info("No new or updated titles found");
            }

        } catch (Exception e) {
            logger.error("Error during data acquisition", e);
            throw e;
        }
    }

    @Async
    public CompletableFuture<Void> performInitialSync() {
        if (isSyncing) {
            logger.warn("Sync already in progress - skipping");
            return CompletableFuture.completedFuture(null);
        }

        try {
            isSyncing = true;
            logger.info("Starting initial data synchronization");

            List<ECFRTitle> titles = ecfrApiService.fetchAllTitles();
            if (!titles.isEmpty()) {
                dataStorageService.saveTitles(titles);
                lastSyncTime = LocalDateTime.now();
                logger.info("Initial sync completed - fetched {} titles", titles.size());
            } else {
                logger.warn("No titles fetched during initial sync");
            }

        } catch (Exception e) {
            logger.error("Error during initial sync", e);
        } finally {
            isSyncing = false;
        }

        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> performIncrementalSync() {
        if (isSyncing) {
            logger.warn("Sync already in progress - skipping");
            return CompletableFuture.completedFuture(null);
        }

        try {
            isSyncing = true;
            logger.info("Starting incremental data synchronization");

            // Check if we have existing data
            boolean hasExistingData = dataStorageService.hasExistingData();

            List<ECFRTitle> updatedTitles;
            if (hasExistingData) {
                // Get last update time and fetch only updated titles
                LocalDateTime lastUpdate = dataStorageService.getLastUpdateTime();
                logger.info("Performing incremental sync since: {}", lastUpdate);
                updatedTitles = ecfrApiService.fetchUpdatedTitles(lastUpdate);
            } else {
                // First time - fetch all titles
                logger.info("No existing data found. Performing initial full sync");
                updatedTitles = ecfrApiService.fetchAllTitles();
            }

            if (!updatedTitles.isEmpty()) {
                // Save the updated/new titles
                dataStorageService.saveTitles(updatedTitles);
                logger.info("Successfully synced {} titles", updatedTitles.size());
            } else {
                logger.info("No new or updated titles found");
            }

        } catch (Exception e) {
            logger.error("Error during incremental sync", e);
        } finally {
            isSyncing = false;
        }

        return CompletableFuture.completedFuture(null);
    }

    // Schedule daily incremental sync at 2 AM
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledSync() {
        logger.info("Starting scheduled daily sync");
        performIncrementalSync();
    }

    public boolean isSyncInProgress() {
        return isSyncing;
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public void forceSync() {
        logger.info("Force sync requested");
        performIncrementalSync();
    }
}
