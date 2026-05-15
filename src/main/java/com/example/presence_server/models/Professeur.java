package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class Professeur extends Utilisateur {
    
    @ManyToMany
    @JoinTable(
        name = "professeur_cours", 
        joinColumns = @JoinColumn(name = "professeur_id"),
        inverseJoinColumns = @JoinColumn(name = "cours_id")
    )
    private List<Cours> coursEnseignes;
}