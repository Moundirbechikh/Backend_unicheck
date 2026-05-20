package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Seance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Integer numeroSeance = 1;
    private String titre;
    
    // NOUVEAUX CHAMPS POUR LE PLANNING
    private String groupe;       // ex: "G1 SITW"
    @ManyToOne
    @JoinColumn(name = "salle_id")
    private Salle salle;
    private String jour;         // ex: "Dimanche"
    private String heurePlage;   // ex: "08:30"
    private String typeSeance;   // ex: "Cours", "TD", "TP"
    
    // Ces valeurs seront initialisées à la création, puis écrasées lors du lancement/arrêt
    private LocalDateTime dateHeureDebut;
    private LocalDateTime dateHeureFin;
    
    private String currentToken;
    private Double profLat;
    private Double profLng;
    
    private boolean estActive = false;
    
    @Column(name = "est_terminee", nullable = false, columnDefinition = "boolean default false")
    private boolean estTerminee = false;

    @Column(name = "is_timer_paused", nullable = false)
    private boolean isTimerPaused = false;
// Après : private LocalDateTime dateHeureFin;
// Ajouter :

// Ajouter ces 2 champs après dateHeureLancement
@Column(name = "token_last_refreshed_at")
private LocalDateTime tokenLastRefreshedAt;

@Column(name = "paused_elapsed_ms")
private Long pausedElapsedMs = 0L;

@Column(name = "date_planifiee")
private LocalDateTime datePlanifiee;      // Date prévue par l'admin (jamais écrasée)

@Column(name = "date_heure_lancement")
private LocalDateTime dateHeureLancement; // Heure réelle de lancement par le prof
    // Déjà défini dans ton architecture : Lié à un Cours et un Professeur[cite: 1]
    @ManyToOne
    private Cours cours;

    @ManyToOne
    private Professeur professeur;
}