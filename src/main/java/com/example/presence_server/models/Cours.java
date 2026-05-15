package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Data
public class Cours {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String libelle;
    private String specialite;
    private Integer nbrHeuresSemaine;

    @ManyToMany(mappedBy = "coursEnseignes")
    private List<Professeur> professeurs;
}