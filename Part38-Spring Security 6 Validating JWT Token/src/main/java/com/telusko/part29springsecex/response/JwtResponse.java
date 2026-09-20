package com.telusko.part29springsecex.response;


import java.util.List;
import org.springframework.lang.NonNull;

public class JwtResponse {

	@NonNull
	private String token;

	public String getToken() {
		return token;
	}

	public JwtResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	private String type = "Bearer";

	@NonNull
	private Long id;

	@NonNull
	private String name;

	@NonNull
	private String username;

	@NonNull
	private String email;

	@NonNull
	private List<String> roles;

	public JwtResponse( String token, String type,
						Long id,  String name,
						String username,  String email,
						List<String> roles) {
		this.token = token;
		this.type = type;
		this.id = id;
		this.name = name;
		this.username = username;
		this.email = email;
		this.roles = roles;
	}

	public JwtResponse( String token,
						Long id,  String name,
						String username,  String email,
						List<String> roles) {
		this.token = token;
		this.type =  "Bearer";
		this.id = id;
		this.name = name;
		this.username = username;
		this.email = email;
		this.roles = roles;
	}

}