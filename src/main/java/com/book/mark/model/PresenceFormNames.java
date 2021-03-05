package com.book.mark.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;


@JsonIgnoreProperties(ignoreUnknown = true)
public class PresenceFormNames {
	
	@JsonProperty("externalId")
	private String externalId;
	
	@JsonProperty("layoutNames")
	private List<String> layoutNames = new ArrayList<String>();

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public List<String> getLayoutNames() {
		return layoutNames;
	}

	public void setLayoutNames(List<String> layoutNames) {
		this.layoutNames = layoutNames;
	}

}
