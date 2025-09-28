package com.vitran.ecfranalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ECFRAgency {

    @JsonProperty("name")
    private String name;

    @JsonProperty("short_name")
    private String shortName;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("sortable_name")
    private String sortableName;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("children")
    private JsonNode children;

    @JsonProperty("cfr_references")
    private JsonNode cfrReferences;

    // Constructors
    public ECFRAgency() {}

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSortableName() { return sortableName; }
    public void setSortableName(String sortableName) { this.sortableName = sortableName; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public JsonNode getChildren() { return children; }
    public void setChildren(JsonNode children) { this.children = children; }

    public JsonNode getCfrReferences() { return cfrReferences; }
    public void setCfrReferences(JsonNode cfrReferences) { this.cfrReferences = cfrReferences; }
}
