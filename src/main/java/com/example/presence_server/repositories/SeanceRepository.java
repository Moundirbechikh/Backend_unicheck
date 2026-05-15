package com.example.presence_server.repositories;

import com.example.presence_server.models.Seance;
import com.example.presence_server.models.Professeur;
import com.example.presence_server.models.Salle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate; // Import ajouté pour corriger l'erreur
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeanceRepository extends JpaRepository<Seance, Long> {

    List<Seance> findByCours_Id(Long coursId);
    List<Seance> findByProfesseur(Professeur professeur);
    List<Seance> findByProfesseur_IdAndDateHeureFinAfterOrderByDateHeureDebutAsc(Long professeurId, LocalDateTime now);
    Optional<Seance> findByProfesseur_IdAndEstActiveTrue(Long professeurId);
    List<Seance> findByCurrentTokenAndEstActiveTrue(String token);
    List<Seance> findByDateHeureDebutBetween(LocalDateTime start, LocalDateTime end);
    List<Seance> findByCours_Specialite(String specialite);
    List<Seance> findByProfesseur_IdAndEstTermineeFalseOrderByDateHeureDebutAsc(Long professeurId);
    List<Seance> findByProfesseur_IdAndDateHeureFinBefore(Long professeurId, LocalDateTime now);
    List<Seance> findByGroupe(String groupe);
    List<Seance> findByJourAndHeurePlageAndProfesseur_Id(String jour, String heurePlage, Long professeurId);
    List<Seance> findByJourAndHeurePlageAndSalle(String jour, String heurePlage, Salle salle);
    boolean existsByJourAndHeurePlageAndProfesseur_IdAndEstTermineeFalse(String jour, String heurePlage, Long professeurId);
    boolean existsByJourAndHeurePlageAndSalleAndEstTermineeFalse(String jour, String heurePlage, Salle salle);
    List<Seance> findByGroupeContainingIgnoreCaseAndDateHeureDebutBetweenOrderByDateHeureDebutAsc(String groupe, LocalDateTime start, LocalDateTime end);
    List<Seance> findByProfesseur_IdAndDateHeureDebutBetweenOrderByDateHeureDebutAsc(Long profId, LocalDateTime start, LocalDateTime end);
    List<Seance> findByGroupeAndEstTermineeTrue(String groupe);
    List<Seance> findByCours_SpecialiteAndEstTermineeTrue(String specialite);
    List<Seance> findByGroupeContainingIgnoreCaseAndEstTermineeTrue(String specialite);
    long countByCours_IdAndCours_SpecialiteAndEstTermineeTrue(Long coursId, String specialite);
    long countByCours_IdAndGroupeAndEstTermineeTrue(Long coursId, String groupe);
    List<Seance> findByGroupeContainingIgnoreCase(String groupe);

    // Prochain cours avec filtre sur date
    List<Seance> findByProfesseur_IdAndEstTermineeFalseAndEstActiveFalseAndDateHeureDebutAfterOrderByDateHeureDebutAsc(
            Long profId, LocalDateTime afterDate);
            
    // Agenda prof : cherche par date planifiée
    List<Seance> findByProfesseur_IdAndDatePlanifieeBetweenOrderByHeurePlageAsc(
            Long profId, LocalDateTime start, LocalDateTime end);

    // Stats-modules : toutes les séances terminées d'un prof
    List<Seance> findByProfesseur_IdAndEstTermineeTrue(Long profId);

    // Séances terminées (affichage date réelle lancement)
    @Query("SELECT s FROM Seance s WHERE s.professeur.id = :profId " +
           "AND s.estTerminee = true " +
           "AND s.dateHeureLancement BETWEEN :start AND :end " +
           "ORDER BY s.heurePlage ASC")
    List<Seance> findTermineesParProfEtDateLancement(
            @Param("profId") Long profId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Séances non terminées (affichage date planifiée)
    @Query("SELECT s FROM Seance s WHERE s.professeur.id = :profId " +
           "AND s.estTerminee = false " +
           "AND COALESCE(s.datePlanifiee, s.dateHeureDebut) BETWEEN :start AND :end " +
           "ORDER BY s.heurePlage ASC")
    List<Seance> findNonTermineesParProfEtDatePlanifiee(
            @Param("profId") Long profId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    
    // Recherche simplifiée par date planifiée
    @Query("SELECT s FROM Seance s WHERE s.professeur.id = :profId " +
           "AND CAST(s.datePlanifiee AS date) = CAST(:date AS date) " +
           "ORDER BY s.heurePlage ASC")
    List<Seance> findByProfAndDatePlanifiee(@Param("profId") Long profId, @Param("date") LocalDate date);
}