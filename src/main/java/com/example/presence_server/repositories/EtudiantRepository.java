package com.example.presence_server.repositories;

import com.example.presence_server.models.Etudiant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {
    List<Etudiant>     findBySpecialite(String specialite);
    List<Etudiant>     findBySpecialiteAndGroupe(String specialite, String groupe);
    List<Etudiant> findByGroupe(String groupe);
    Optional<Etudiant> findByDeviceId(String deviceId);
    Optional<Etudiant> findByCodeQrFixe(String codeQrFixe);
    Optional<Etudiant> findByEmail(String email);
    Optional<Etudiant> findByMatricule(String matricule);   // ← NOUVEAU
    boolean            existsByMatricule(String matricule); // ← NOUVEAU
}