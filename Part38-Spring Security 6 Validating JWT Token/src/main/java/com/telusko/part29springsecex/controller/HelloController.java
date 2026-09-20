package com.telusko.part29springsecex.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	// Sin request.getSession(): la API es stateless y no debe crear JSESSIONID
	@GetMapping("/")
    public String greet(Authentication authentication) {
        return "Welcome to Telusko " + authentication.getName();
    }

}
