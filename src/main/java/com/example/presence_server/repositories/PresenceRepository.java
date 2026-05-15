package com.example.presence_server.repositories;

import com.example.presence_server.models.Presence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PresenceRepository extends JpaRepository<Presence, Long> {

    // Pour l'Étudiant : Voir ses présences pour un cours spécifique
    List<Presence> findByEtudiant_IdAndSeance_Cours_Id(Long etudiantId, Long coursId);

    // Pour le Professeur : Liste des présents en temps réel
    List<Presence> findBySeance_Id(Long seanceId);

    // Pour les stats : Compter par statut (PRESENT, RETARD)
    long countByEtudiant_IdAndSeance_Cours_IdAndStatutPresence(Long etudiantId, Long coursId, String statut);

    // Anti-triche : Vérifier si déjà pointé
    Optional<Presence> findByEtudiant_IdAndSeance_Id(Long etudiantId, Long seanceId);

    // Pour l'Admin : Trouver par statut (ex: "SUSPECT")
    List<Presence> findByStatutPresence(String statut);

    // Historique : Sur une période donnée
    List<Presence> findByEtudiant_IdAndHeurePointageBetween(Long etudiantId, LocalDateTime start, LocalDateTime end);

    // Pour le Professeur : Présences suspectes sur ses séances
    List<Presence> findBySeance_Professeur_IdAndStatutPresence(Long professeurId, String statut);

    // NOUVEAU : Compter toutes les présences d'un étudiant pour un cours donné
    long countByEtudiant_IdAndSeance_Cours_Id(Long etudiantId, Long coursId);
}