package com.example.presence_server.repositories;

import com.example.presence_server.models.Cours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoursRepository extends JpaRepository<Cours, Long> {

    // Pour l'Admin : Trouver un cours par son libellé exact (ex: "Théorie des graphes")
    Optional<Cours> findByLibelle(String libelle);

    // Pour l'Admin : Lister tous les cours d'un parcours (ex: "L2 Informatique")
    List<Cours> findBySpecialite(String specialite);

    // Pour l'Admin : Rechercher un cours par une partie de son nom
    List<Cours> findByLibelleContainingIgnoreCase(String keyword);

    // Pour l'interface Professeur : Trouver tous les cours assignés à un professeur spécifique
    List<Cours> findByProfesseurs_Id(Long professeurId);
}