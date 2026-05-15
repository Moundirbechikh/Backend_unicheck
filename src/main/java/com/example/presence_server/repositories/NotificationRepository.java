package com.example.presence_server.repositories;

import com.example.presence_server.models.Notification;
import com.example.presence_server.models.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUtilisateurOrderByDateCreationDesc(Utilisateur utilisateur);
    List<Notification> findByUtilisateurAndEstLueFalse(Utilisateur utilisateur);
    long countByUtilisateurAndEstLueFalse(Utilisateur utilisateur);
    long countByUtilisateurAndGravite(Utilisateur utilisateur, String gravite);
    List<Notification> findByGravite(String gravite);
    void deleteByDateCreationBefore(LocalDateTime date);

    // ✅ Supprimer toutes les notifs de lancement d'une séance précise
    void deleteBySeanceIdAndTypeNotification(Long seanceId, String typeNotification);

    // ✅ Trouver les notifs par séance et type
    List<Notification> findBySeanceIdAndTypeNotification(Long seanceId, String typeNotification);
}