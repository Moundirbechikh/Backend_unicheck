package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Presence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime heurePointage;
    private Double studentLat;
    private Double studentLng;
    private String deviceUsed;
    private String statutPresence; // ex: PRESENT, RETARD, SUSPECT
    private String methodeScan;    // ex: DYNAMIQUE, MANUEL

    @ManyToOne
    private Etudiant etudiant;

    @ManyToOne
    private Seance seance;
}