package com.fbcorp.gleo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_preferences")
@Getter
@Setter
public class AdminPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Column(length = 50)
    private String theme; // e.g. 'light' or 'dark'

    @Column(columnDefinition = "text")
    private String menuOrderJson;
}
