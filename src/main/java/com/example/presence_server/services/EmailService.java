package com.example.presence_server.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void envoyerAlerteAbsence(String toEmail, String nomEtudiant, String module, String nomProf, long nbAbsences) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck : Alerte d'assiduité - " + module);

            // Template HTML inspiré de la Landing Page UniCheck
            String htmlContent = "<div style=\"font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #fcfdfe; padding: 40px 20px; color: #4B5563;\">"
                    + "<div style=\"max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 24px; padding: 40px; box-shadow: 0 10px 30px -5px rgba(0,0,0,0.05); border: 1px solid #f1f4f2;\">"
                    
                    // Header avec Logo "fait maison" en CSS
                    + "<div style=\"text-align: center; margin-bottom: 30px;\">"
                    + "<div style=\"display: inline-block; width: 40px; height: 40px; background-color: #006c49; border-radius: 12px; position: relative;\">"
                    + "<div style=\"width: 10px; height: 10px; background-color: white; border-radius: 50%; position: absolute; top: 15px; left: 15px;\"></div>"
                    + "</div>"
                    + "<h1 style=\"color: #1a1c1e; font-size: 24px; font-weight: 800; margin-top: 15px; letter-spacing: -1px;\">Unicheck</h1>"
                    + "</div>"

                    // Corps du message
                    + "<div style=\"background-color: #fff5f5; border-left: 4px solid #ef4444; padding: 15px 20px; border-radius: 8px; margin-bottom: 25px;\">"
                    + "<p style=\"color: #b91c1c; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; margin: 0;\">Alerte d'assiduité</p>"
                    + "</div>"

                    + "<h2 style=\"color: #1a1c1e; font-size: 20px; font-weight: 700;\">Bonjour " + nomEtudiant + ",</h2>"
                    + "<p style=\"font-size: 15px; line-height: 1.6; color: #4B5563;\">"
                    + "Notre système a détecté que vous avez atteint <strong>" + nbAbsences + " absences</strong> pour le module <strong>" + module + "</strong> dispensé par <strong>" + nomProf + "</strong>."
                    + "</p>"
                    + "<p style=\"font-size: 15px; line-height: 1.6; color: #4B5563;\">"
                    + "Ce niveau d'absence est critique et peut impacter la validation de votre semestre. Nous vous invitons à régulariser votre situation au plus vite en soumettant un justificatif via votre espace personnel ou en contactant l'administration."
                    + "</p>"

                    // Bouton d'action
                    + "<div style=\"text-align: center; margin-top: 35px;\">"
                    + "<a href=\"http://localhost:3000/connexion\" style=\"background-color: #1a1c1e; color: #ffffff; padding: 16px 32px; text-decoration: none; border-radius: 30px; font-weight: 700; font-size: 14px; display: inline-block;\">Accéder à mon espace</a>"
                    + "</div>"

                    // Footer
                    + "<div style=\"margin-top: 40px; padding-top: 20px; border-top: 1px solid #f1f4f2; text-align: center;\">"
                    + "<p style=\"color: #9ca3af; font-size: 11px; text-transform: uppercase; letter-spacing: 2px;\">© 2026 UniCheck • Designed for Excellence</p>"
                    + "</div>"

                    + "</div>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("📧 E-mail d'alerte envoyé avec succès à " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ Erreur lors de l'envoi de l'e-mail : " + e.getMessage());
        }
    }
    // Dans EmailService.java
public void envoyerMailBienvenue(String toEmail, String nomEtudiant) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("🚀 Bienvenue sur UniCheck — Votre espace est prêt !");

        String htmlContent = 
            "<div style=\"font-family: 'Inter', Helvetica, Arial, sans-serif; background-color: #fcfdfe; padding: 60px 20px; color: #1a1c1e;\">" +
            "  <div style=\"max-width: 550px; margin: 0 auto; background-color: #ffffff; border-radius: 32px; padding: 48px; box-shadow: 0 20px 40px rgba(0,0,0,0.03); border: 1px solid #f1f5f9;\">" +
            
            "    <div style=\"text-align: center; margin-bottom: 40px;\">" +
            "      <div style=\"display: inline-block; width: 48px; height: 48px; background-color: #006c49; border-radius: 14px; margin-bottom: 20px;\">" +
            "        <div style=\"width: 12px; height: 12px; background-color: white; border-radius: 50%; margin: 18px auto;\"></div>" +
            "      </div>" +
            "      <h1 style=\"font-size: 28px; font-weight: 800; letter-spacing: -1px; margin: 0;\">UniCheck</h1>" +
            "    </div>" +

            "    <div style=\"text-align: center; margin-bottom: 32px;\">" +
            "      <h2 style=\"font-size: 22px; font-weight: 700; color: #1a1c1e;\">Bonjour " + nomEtudiant + ",</h2>" +
            "      <p style=\"font-size: 16px; line-height: 1.7; color: #64748b;\">" +
            "        L'administration vient d'activer votre compte sur la nouvelle plateforme de gestion d'assiduité. " +
            "        Désormais, votre présence est sécurisée, automatisée et consultable en temps réel." +
            "      </p>" +
            "    </div>" +

            "    <div style=\"background-color: #f8fafc; border-radius: 24px; padding: 24px; margin-bottom: 32px;\">" +
            "      <div style=\"display: flex; align-items: center; margin-bottom: 12px;\">" +
            "        <span style=\"color: #006c49; font-weight: 800; font-size: 14px; text-transform: uppercase; letter-spacing: 1px;\">Ce qui change pour vous :</span>" +
            "      </div>" +
            "      <ul style=\"margin: 0; padding-left: 20px; color: #475569; font-size: 14px; line-height: 2;\">" +
            "        <li>Scannez votre badge via QR Code</li>" +
            "        <li>Recevez des alertes d'absence instantanées</li>" +
            "        <li>Justifiez vos absences directement en ligne</li>" +
            "        <li>Suivez vos statistiques par module</li>" +
            "      </ul>" +
            "    </div>" +

            "    <div style=\"text-align: center;\">" +
            "      <a href=\"http://localhost:3000\" style=\"display: inline-block; background-color: #1a1c1e; color: #ffffff; padding: 18px 36px; border-radius: 20px; text-decoration: none; font-weight: 700; font-size: 15px; box-shadow: 0 10px 20px rgba(0,0,0,0.1);\">Accéder à mon espace</a>" +
            "    </div>" +

            "    <p style=\"text-align: center; margin-top: 40px; font-size: 12px; color: #94a3b8; line-height: 1.6;\">" +
            "      Ceci est un message automatique de test pour confirmer la configuration de votre compte.<br/>" +
            "      © 2026 UniCheck • Excellence Académique" +
            "    </p>" +
            "  </div>" +
            "</div>";

        helper.setText(htmlContent, true);
        mailSender.send(message);
    } catch (Exception e) {
        System.err.println("Erreur mail : " + e.getMessage());
    }
}
}