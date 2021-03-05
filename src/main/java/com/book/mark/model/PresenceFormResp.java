package com.book.mark.model;

public class PresenceFormResp {
	
	private String message;
	private String validLayouts;
	private String invalidLayouts;
	
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getValidLayouts() {
		return validLayouts;
	}
	public void setValidLayouts(String validLayouts) {
		this.validLayouts = validLayouts;
	}
	public String getInvalidLayouts() {
		return invalidLayouts;
	}
	public void setInvalidLayouts(String invalidLayouts) {
		this.invalidLayouts = invalidLayouts;
	}

}
