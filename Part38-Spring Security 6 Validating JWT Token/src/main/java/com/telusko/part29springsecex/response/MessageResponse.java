package com.telusko.part29springsecex.response;

import org.springframework.lang.NonNull;

public class MessageResponse {

	public MessageResponse(@NonNull String message) {
		super();
		this.message = message;
	}

	public MessageResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	@NonNull
	private String message;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}