package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Data
public class CoursAssignation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cours_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "assignations", "professeurs"})
    private Cours cours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professeur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "coursEnseignes"})
    private Professeur professeur;

    // COURS | TD | TP
    private String typeSeance;
}