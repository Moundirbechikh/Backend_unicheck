package com.example.presence_server.models;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class Etudiant extends Utilisateur {
    private String matricule; // Ajouté pour corriger l'erreur de compilation
    private String codeQrFixe;
    private String specialite;
    private String anneeUniversitaire;
    private String groupe;
    private String deviceId;
}