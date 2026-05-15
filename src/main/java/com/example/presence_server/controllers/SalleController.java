package com.example.presence_server.controllers;

import com.example.presence_server.models.Salle;
import com.example.presence_server.repositories.SalleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salles")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class SalleController {

    @Autowired
    private SalleRepository salleRepository;

    @GetMapping
    public List<Salle> getAllSalles() {
        return salleRepository.findAll();
    }

    @PostMapping("/add")
    public ResponseEntity<?> addSalle(@RequestBody Salle salle) {
        Salle savedSalle = salleRepository.save(salle);
        return ResponseEntity.ok(savedSalle);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSalle(@PathVariable Long id) {
        if (!salleRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Salle non trouvée"));
        }
        salleRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Salle supprimée avec succès"));
    }
}