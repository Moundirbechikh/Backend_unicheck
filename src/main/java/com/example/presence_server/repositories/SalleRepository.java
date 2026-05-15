package com.example.presence_server.repositories;

import com.example.presence_server.models.Salle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalleRepository extends JpaRepository<Salle, Long> {
    // Tu peux ajouter une recherche par nom si besoin plus tard
    Salle findByNom(String nom);
}