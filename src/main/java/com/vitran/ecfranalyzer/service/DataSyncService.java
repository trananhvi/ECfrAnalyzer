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

    private volatile boolean isSyncing = false;
    private LocalDateTime lastSyncTime;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        logger.info("Application started - checking for existing data");

        if (!dataStorageService.hasExistingData()) {
            logger.info("No existing data found - starting initial data fetch");
            performInitialSync();
        } else {
            logger.info("Existing data found - checking if update is needed");
            LocalDateTime lastUpdate = dataStorageService.getLastUpdateTime();
            if (lastUpdate == null || ChronoUnit.HOURS.between(lastUpdate, LocalDateTime.now()) > 24) {
                logger.info("Data is outdated - starting incremental sync");
                performIncrementalSync();
            } else {
                logger.info("Data is up to date");
                lastSyncTime = lastUpdate;
            }
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
            LocalDateTime since = dataStorageService.getLastUpdateTime();
            logger.info("Starting incremental sync since: {}", since);

            List<ECFRTitle> updatedTitles = ecfrApiService.fetchUpdatedTitles(since);

            if (!updatedTitles.isEmpty()) {
                // For simplicity, we're replacing all data
                // In production, you'd merge the updates
                dataStorageService.saveTitles(updatedTitles);
                lastSyncTime = LocalDateTime.now();
                logger.info("Incremental sync completed - processed {} titles", updatedTitles.size());
            } else {
                logger.info("No new updates found during incremental sync");
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
