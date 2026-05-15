package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Justificatif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Fichier
    private String fichierPdfUrl;   // chemin sur le disque
    private String nomFichierOriginal; // nom original pour affichage

    // Motif
    private String typeMotif;          // Médical, Administratif, Personnel, Autre
    private String motif;              // texte libre optionnel

    private LocalDateTime dateSoumission;

    // EN_ATTENTE, ACCEPTE, REFUSE
    private String statutValidation;

    // Commentaire du prof lors de la décision
    private String commentaireProf;

    @ManyToOne
    private Etudiant etudiant;

    @ManyToOne  // ← ManyToOne car une séance peut avoir plusieurs justificatifs (un par étudiant)
    private Seance seance;
}