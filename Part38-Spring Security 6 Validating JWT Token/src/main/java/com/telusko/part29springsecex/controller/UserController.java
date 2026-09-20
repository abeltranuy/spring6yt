package com.telusko.part29springsecex.controller;

import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.request.AuthRequest;
import com.telusko.part29springsecex.response.AuthResponse;
import com.telusko.part29springsecex.response.MessageResponse;
import com.telusko.part29springsecex.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService service;

    @PostMapping("/registrar")
    public ResponseEntity<Users> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    // Credenciales invalidas -> AuthenticationException -> 401 en GlobalExceptionHandler
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request) {
        AuthResponse authenticationDetails = service.verify(request);
        return ResponseEntity.status(HttpStatus.OK)
                .header("Authorization", "Bearer " + authenticationDetails.token())
                .body(authenticationDetails);
    }

    // El LogoutFilter por defecto esta deshabilitado en SecurityConfig; si no, interceptaria /logout
    // y este metodo nunca se ejecutaria.
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        log.info("llego al PostMApping logout");

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            service.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(new MessageResponse("Logged Out Successfully"));
    }

    /*

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request){
        System.out.println("logging out");
        request.getSession().removeAttribute("SPRING_SECURITY_CONTEXT");
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return new ResponseEntity<>("Logged out successfully", HttpStatus.OK);
    }

    @PostMapping("/signout")
    public ResponseEntity<?> logoutUser(HttpServletResponse response) {
        authenticationService.logoutUser(response);
        return new ResponseEntity("You've been signed out!", HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Users user) throws IOException {

    	System.out.println("llego a ver el login");
    	String token = service.verify(user);

    	// response.setHeader("Authorization", "Bearer " + token);
    	// response.setStatus(200);
    	// response.getWriter().println("Hello World!");

   	   	//HttpHeaders responseHeaders = new HttpHeaders();
   	   	//responseHeaders.setLocation(location);
   	   	//responseHeaders.set("Authorization", "Bearer " + token);
   	   	return new ResponseEntity<String>(token, HttpStatus.ACCEPTED);
	}
    */

}
