package com.example.presence_server.repositories;

import com.example.presence_server.models.CoursAssignation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CoursAssignationRepository extends JpaRepository<CoursAssignation, Long> {
    List<CoursAssignation> findByCours_Id(Long coursId);
    List<CoursAssignation> findByProfesseur_Id(Long profId);
    List<CoursAssignation> findByCours_IdAndTypeSeance(Long coursId, String typeSeance);
    void deleteByCours_Id(Long coursId);
}