package com.example.presence_server.controllers;

import com.example.presence_server.models.Utilisateur;
import com.example.presence_server.repositories.UserRepositorie;
import com.example.presence_server.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepositorie userRepository; 
    
    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        try {
            String email = credentials.get("email");
            String password = credentials.get("password");

            Optional<Utilisateur> userOpt = userRepository.findByEmail(email);

            if (userOpt.isPresent() && userOpt.get().getMotDePasse().equals(password)) {
                Utilisateur user = userOpt.get();
                
                // Détermination du rôle par nom de classe ou logique personnalisée
                String role = user.getClass().getSimpleName().toLowerCase();
                if (role.equals("administrateur")) role = "admin";
                else if (role.equals("professeur")) role = "prof";
                else if (role.equals("etudiant")) role = "etudiant";

                String token = jwtService.generateToken(user.getId(), user.getEmail(), role);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("token", token);
                response.put("role", role);
                response.put("userId", user.getId());
                response.put("nom", user.getNom() != null ? user.getNom() : "");
                response.put("prenom", user.getPrenom() != null ? user.getPrenom() : "");

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Identifiants incorrects");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
        } catch (Exception e) {
            e.printStackTrace(); 
            Map<String, Object> serverError = new HashMap<>();
            serverError.put("success", false);
            serverError.put("message", "Erreur interne: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(serverError);
        }
    }
}