package com.telusko.part29springsecex.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class UserPrincipal implements UserDetails {

	private static final long serialVersionUID = 2115107329014515698L;
	
	private Users user;

    public UserPrincipal(Users user) {
        this.user = user;
    }

    /*
    public UserPrinciple(Long id, String name,
            String username, String email, String password,
            Collection<? extends GrantedAuthority> authorities) {
		this.id = id;
		this.name = name;
		this.username = username;
		this.email = email;
		this.password = password;
		this.authorities = authorities;
	}
		
	public static UserPrinciple build(User user) {
		List<GrantedAuthority> authorities = user.getRoles().stream().map(role ->
		   new SimpleGrantedAuthority(role.getName().name())
		).collect(Collectors.toList());
		
		return new UserPrinciple(
		   user.getId(),
		   user.getName(),
		   user.getUsername(),
		   user.getEmail(),
		   user.getPassword(),
		   authorities
		);
	}
	*/
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //return Collections.singleton(new SimpleGrantedAuthority("USER"));
    	return List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }


    @JsonIgnore
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
    
}