package com.example.presence_server.repositories;

import com.example.presence_server.models.Justificatif;
import com.example.presence_server.models.Etudiant;
import com.example.presence_server.models.Seance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface JustificatifRepository extends JpaRepository<Justificatif, Long> {

    List<Justificatif> findByStatutValidation(String statutValidation);
    List<Justificatif> findByEtudiant(Etudiant etudiant);
    List<Justificatif> findByEtudiantOrderByDateSoumissionDesc(Etudiant etudiant);

    Optional<Justificatif> findByEtudiantAndSeance(Etudiant etudiant, Seance seance);
    Boolean existsByEtudiantAndSeance(Etudiant etudiant, Seance seance);

    List<Justificatif> findByEtudiant_Specialite(String specialite);

    // ✅ Pour le prof : voir les justificatifs de ses séances
    List<Justificatif> findBySeance_Professeur_Id(Long professeurId);
    List<Justificatif> findBySeance_Professeur_IdAndStatutValidation(
            Long professeurId, String statut);

    long countBySeance_Professeur_IdAndStatutValidation(Long professeurId, String statut);
}