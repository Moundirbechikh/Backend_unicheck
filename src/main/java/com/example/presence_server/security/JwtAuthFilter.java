package com.example.presence_server.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // ✅ 1. Gérer la requête de pré-vérification (CORS Preflight)
        // On laisse passer la requête OPTIONS pour que le WebConfig s'occupe des headers
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();

        // ✅ 2. Routes publiques — pas de token requis
        if (path.startsWith("/api/auth") || path.startsWith("/api/inscription")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 3. Vérification du token JWT pour les routes protégées
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token manquant\"}");
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.extractAllClaims(token);
            request.setAttribute("userId", claims.get("userId"));
            request.setAttribute("role",   claims.get("role"));
            
            // Si le token est bon, on continue vers le Controller
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            System.err.println("Erreur de validation JWT : " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token invalide ou expire\"}");
        }
    }
}