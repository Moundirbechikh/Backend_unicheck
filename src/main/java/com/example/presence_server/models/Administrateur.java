package com.example.presence_server.models;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class Administrateur extends Utilisateur {
    // Cette classe hérite de nom, prenom, email, motDePasse via Utilisateur
}