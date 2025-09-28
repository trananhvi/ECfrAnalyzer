package com.vitran.ecfranalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ECFRApiResponse {

    @JsonProperty("titles")
    private List<ECFRTitle> titles;

    @JsonProperty("meta")
    private Map<String, Object> meta;

    // Legacy fields for backward compatibility
    private int count;
    private String next;
    private String previous;
    private List<ECFRTitle> results;

    // Constructors
    public ECFRApiResponse() {}

    // New getters and setters for actual API structure
    public List<ECFRTitle> getTitles() { return titles; }
    public void setTitles(List<ECFRTitle> titles) { this.titles = titles; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    // Legacy getters for backward compatibility
    public int getCount() { return titles != null ? titles.size() : 0; }
    public void setCount(int count) { this.count = count; }

    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }

    public String getPrevious() { return previous; }
    public void setPrevious(String previous) { this.previous = previous; }

    public List<ECFRTitle> getResults() { return titles; }
    public void setResults(List<ECFRTitle> results) { this.results = results; this.titles = results; }
}
