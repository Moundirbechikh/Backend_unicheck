package com.example.presence_server.repositories;

import com.example.presence_server.models.Professeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional; // L'import manquant était ici

@Repository
public interface ProfesseurRepository extends JpaRepository<Professeur, Long> {

    // Pour l'admin : Trouver tous les professeurs qui enseignent un cours spécifique
    // Spring Boot comprend tout seul qu'il doit regarder dans la liste "coursEnseignes"
    List<Professeur> findByCoursEnseignes_Id(Long coursId);

    // Pour l'admin : Rechercher un professeur par son nom (pratique s'il y en a beaucoup)
    List<Professeur> findByNomContainingIgnoreCase(String nom);
    
    // Pour l'admin : Voir si un prof est déjà assigné à un certain cours (par son libellé)
    List<Professeur> findByCoursEnseignes_Libelle(String libelle);
    Optional<Professeur> findByEmail(String email);
}