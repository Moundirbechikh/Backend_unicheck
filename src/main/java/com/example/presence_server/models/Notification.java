package com.example.presence_server.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;
    private String titre;
    private LocalDateTime dateCreation;

    // VERT, ORANGE, ROUGE
    private String gravite;

    private boolean estLue = false;

    // ✅ Pour retrouver et supprimer les notifs liées à une séance
    private Long seanceId;

    // ✅ LANCEMENT ou FIN — permet de supprimer les notifs de lancement à la fin
    private String typeNotification;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;
}