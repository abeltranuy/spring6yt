package com.telusko.part29springsecex.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.telusko.part29springsecex.enums.RoleList;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// La restriccion va a nivel de tabla y con nombre porque ddl-auto=update solo aplica
// @Column(unique = true) a columnas nuevas; asi tambien la crea sobre una tabla users ya existente.
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(nullable = false)
    private String username;
    @JsonIgnore
    @Column(nullable = false)
    private String password;
    @Enumerated(EnumType.STRING)
    private RoleList role;
    
    public RoleList getRole() {
		return role;
	}

	public void setRole(RoleList role) {
		this.role = role;
	}

	public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
	public String toString() {
		return "Users{id=" + id + ", username=" + username + ", role=" + role + "}";
	}
}
