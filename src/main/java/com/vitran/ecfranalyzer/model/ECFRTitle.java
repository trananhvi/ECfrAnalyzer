package com.vitran.ecfranalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ECFRTitle {

    @JsonProperty("number")
    private int number;

    @JsonProperty("name")
    private String name;

    @JsonProperty("reserved")
    private boolean reserved;

    @JsonProperty("latest_amended_on")
    private String latestAmendedOn;

    @JsonProperty("latest_issue_date")
    private String latestIssueDate;

    @JsonProperty("up_to_date_as_of")
    private String upToDateAsOf;

    // Additional metadata we'll add during processing
    private String agency;
    private String content;
    private LocalDateTime lastUpdated;
    private String checksum;
    private int wordCount;
    private String structureData;

    // Constructors
    public ECFRTitle() {}

    public ECFRTitle(String number, String name, String agency) {
        try {
            this.number = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            this.number = 0;
        }
        this.name = name;
        this.agency = agency;
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and Setters
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isReserved() { return reserved; }
    public void setReserved(boolean reserved) { this.reserved = reserved; }

    public String getLatestAmendedOn() { return latestAmendedOn; }
    public void setLatestAmendedOn(String latestAmendedOn) { this.latestAmendedOn = latestAmendedOn; }

    public String getLatestIssueDate() { return latestIssueDate; }
    public void setLatestIssueDate(String latestIssueDate) { this.latestIssueDate = latestIssueDate; }

    public String getUpToDateAsOf() { return upToDateAsOf; }
    public void setUpToDateAsOf(String upToDateAsOf) { this.upToDateAsOf = upToDateAsOf; }

    // Legacy getters for backward compatibility
    public String getTitleNumber() { return String.valueOf(number); }
    public void setTitleNumber(String titleNumber) {
        try {
            this.number = Integer.parseInt(titleNumber);
        } catch (NumberFormatException e) {
            this.number = 0;
        }
    }

    public String getTitleName() { return name; }
    public void setTitleName(String titleName) { this.name = titleName; }

    public String getAgency() { return agency; }
    public void setAgency(String agency) { this.agency = agency; }

    public String getContent() { return content; }
    public void setContent(String content) {
        this.content = content;
        this.wordCount = content != null ? content.split("\\s+").length : 0;
    }

    // Legacy getter/setter for text
    public String getText() { return content; }
    public void setText(String text) { setContent(text); }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public String getStructureData() { return structureData; }
    public void setStructureData(String structureData) { this.structureData = structureData; }

    public String getDate() { return latestIssueDate; }
    public void setDate(String date) { this.latestIssueDate = date; }

    public String getChapter() { return null; }
    public void setChapter(String chapter) { /* Not used in new API */ }

    public String getPart() { return null; }
    public void setPart(String part) { /* Not used in new API */ }

    public String getSection() { return null; }
    public void setSection(String section) { /* Not used in new API */ }
}
