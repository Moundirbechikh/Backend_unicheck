package com.example.presence_server.repositories;

import com.example.presence_server.models.Administrateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AdministrateurRepository extends JpaRepository<Administrateur, Long> {

    // Pour la connexion : Trouver l'admin par son email (hérité de Utilisateur)
 Optional<Administrateur> findByEmail(String email);

    // Pour vérifier si un compte admin existe déjà au lancement de l'app
    boolean existsByEmail(String email);
}
