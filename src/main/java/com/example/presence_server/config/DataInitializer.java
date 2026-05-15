package com.example.presence_server.config;
import com.example.presence_server.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initDatabase(

            UserRepositorie userRepo,
            EtudiantRepository etudiantRepo,
            ProfesseurRepository profRepo,
            AdministrateurRepository adminRepo,
            CoursRepository coursRepo,
            SeanceRepository seanceRepo) {

        return args -> {

            // Configuration vidée intentionnellement. 

            // Aucune donnée d'initialisation ne sera injectée au démarrage du serveur.

            System.out.println("\n--- INITIALISATION DES DONNÉES DÉSACTIVÉE --- \n");

        };
    }
}