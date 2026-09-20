package com.telusko.part29springsecex.response;

import java.util.List;

public record AuthResponse(String token, String type, String username, List<String> roles) {

    public AuthResponse(String token, String username, List<String> roles) {
        this(token, "Bearer", username, roles);
    }
}
