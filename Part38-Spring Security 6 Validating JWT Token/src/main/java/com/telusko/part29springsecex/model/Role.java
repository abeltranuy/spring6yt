package com.telusko.part29springsecex.model;

import com.telusko.part29springsecex.enums.RoleList;
import jakarta.persistence.*;

@Entity
public class Role { 

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RoleList name;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public RoleList getName() {
		return name;
	}

	public void setName(RoleList name) {
		this.name = name;
	}
}
