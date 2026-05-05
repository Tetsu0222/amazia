package com.example.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screen_id", unique = true, nullable = false)
    private String screenId;

    @Column(nullable = false)
    private String name;

    public Long getId() { return id; }
    public String getScreenId() { return screenId; }
    public String getName() { return name; }
}
