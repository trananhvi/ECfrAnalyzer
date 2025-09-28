package com.vitran.ecfranalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ECFRAgenciesResponse {

    @JsonProperty("agencies")
    private List<ECFRAgency> agencies;

    // Constructors
    public ECFRAgenciesResponse() {}

    // Getters and Setters
    public List<ECFRAgency> getAgencies() { return agencies; }
    public void setAgencies(List<ECFRAgency> agencies) { this.agencies = agencies; }
}
