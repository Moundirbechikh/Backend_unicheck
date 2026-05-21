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

    private static final String BRAND_COLOR = "#1a1c1e";
    private static final String ACCENT_EMERALD = "#10b981";

    // --- LOGO REUSABLE EN PUR CSS (REPRODUIT LA GRILLE REACT) ---
    private String getLogoHtml() {
        return """
            <div style="text-align: center; margin-bottom: 32px;">
                <div style="display: inline-block; width: 64px; height: 64px; background-color: #1a1c1e; border-radius: 18px; padding: 14px; box-sizing: border-box; transform: rotate(3deg); -webkit-transform: rotate(3deg); -moz-transform: rotate(3deg); box-shadow: 0 10px 25px rgba(0,0,0,0.15);">
                    <table border="0" cellpadding="0" cellspacing="4" style="width: 100%; height: 100%;">
                        <tr>
                            <td style="background-color: #10b981; border-radius: 4px; width: 50%; height: 50%;"></td>
                            <td style="background-color: #ffffff; border-radius: 4px;"></td>
                        </tr>
                        <tr>
                            <td style="background-color: #ffffff; border-radius: 4px;"></td>
                            <td style="background-color: #ffffff; border-radius: 4px;"></td>
                        </tr>
                    </table>
                </div>
                <h1 style="color: #1a1c1e; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 26px; font-weight: 900; margin-top: 16px; margin-bottom: 0; letter-spacing: -1px;">UniCheck QR</h1>
            </div>
        """;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. ALERTE ABSENCE RE-DESIGNÉE
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerAlerteAbsence(String toEmail, String nomEtudiant, String module, String nomProf, long nbAbsences) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck : Alerte d'assiduité critique — " + module);

            String htmlContent = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 50px 20px; color: #334155;">
                    <div style="max-width: 560px; margin: 0 auto; background-color: #ffffff; border-radius: 32px; padding: 40px; box-shadow: 0 20px 40px rgba(15, 23, 42, 0.04); border: 1px solid #f1f5f9;">
                        
                        """ + getLogoHtml() + """
                        
                        <div style="background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 16px 20px; border-radius: 12px; margin-bottom: 32px; text-align: left;">
                            <p style="color: #b91c1c; font-weight: 800; font-size: 11px; text-transform: uppercase; letter-spacing: 1.5px; margin: 0;">Seuil Critique Atteint</p>
                        </div>

                        <h2 style="color: #1a1c1e; font-size: 22px; font-weight: 800; margin-top: 0; margin-bottom: 16px; letter-spacing: -0.5px;">Bonjour %s,</h2>
                        <p style="font-size: 15px; line-height: 1.7; color: #475569; margin-bottom: 24px;">
                            Le système automatisé d'assiduité a détecté une situation nécessitant votre attention immédiate pour le module <strong>%s</strong> (Enseigné par : <strong>%s</strong>).
                        </p>

                        <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 20px; padding: 24px; text-align: center; margin-bottom: 32px;">
                            <span style="font-size: 13px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 1px; display: block; margin-bottom: 4px;">Votre état actuel</span>
                            <span style="font-size: 48px; font-weight: 900; color: #ef4444; line-height: 1;">%d</span>
                            <span style="font-size: 15px; font-weight: 700; color: #ef4444; display: block; margin-top: 4px;">absences cumulées</span>
                        </div>

                        <p style="font-size: 14px; line-height: 1.6; color: #64748b; margin-bottom: 32px; background-color: #fffbeb; border: 1px solid #fef3c7; pading: 12px; border-radius: 12px; padding: 16px;">
                            💡 <strong>Rappel :</strong> Conformément au règlement intérieur, dépasser ce quota compromet directement la validation de votre semestre. Veuillez téléverser vos justificatifs médicaux ou administratifs depuis votre espace en ligne sans plus attendre.
                        </p>

                        <div style="text-align: center;">
                            <a href="http://localhost:3000/connexion" style="display: inline-block; background-color: #1a1c1e; color: #ffffff; padding: 18px 36px; text-decoration: none; border-radius: 24px; font-weight: 800; font-size: 15px; box-shadow: 0 10px 25px rgba(26,28,30,0.25); transition: all 0.2s ease;">Déposer un justificatif</a>
                        </div>

                        <div style="margin-top: 48px; padding-top: 24px; border-top: 1px solid #f1f5f9; text-align: center;">
                            <p style="color: #94a3b8; font-size: 11px; text-transform: uppercase; letter-spacing: 1.5px; margin: 0;">© 2026 UniCheck • Notification d'Assiduité</p>
                        </div>

                    </div>
                </div>
            """.formatted(nomEtudiant, module, nomProf, nbAbsences);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("📧 E-mail d'alerte envoyé avec succès à " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ Erreur lors de l'envoi de l'e-mail d'alerte : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. BIENVENUE COMPTE ACTIVÉ (ÉTUDIANT ET PROFESSEUR)
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerMailBienvenue(String toEmail, String nomUtilisateur, String role) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("🚀 Bienvenue sur UniCheck — Votre espace " + role + " est opérationnel !");

            String specificDetails = role.equalsIgnoreCase("Enseignant") ? """
                <li>Générez vos QR Codes dynamiques rafraîchis toutes les 5s</li>
                <li>Suivez en direct le flux d'arrivée des étudiants</li>
                <li>Validez et gérez l'historique des présences par groupe</li>
                <li>Consultez les statistiques d'assiduité globales</li>
            """ : """
                <li>Scannez vos badges via QR Code de présence</li>
                <li>Recevez des alertes d'absence instantanées par module</li>
                <li>Justifiez vos absences directement depuis votre compte</li>
                <li>Suivez votre feuille de présence consolidée</li>
            """;

            String htmlContent = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 50px 20px; color: #334155;">
                    <div style="max-width: 560px; margin: 0 auto; background-color: #ffffff; border-radius: 32px; padding: 40px; box-shadow: 0 20px 40px rgba(15, 23, 42, 0.04); border: 1px solid #f1f5f9;">
                        
                        """ + getLogoHtml() + """

                        <div style="text-align: center; margin-bottom: 32px;">
                            <h2 style="color: #1a1c1e; font-size: 24px; font-weight: 800; margin-top: 0; margin-bottom: 12px; letter-spacing: -0.5px;">Bonjour %s,</h2>
                            <p style="font-size: 16px; line-height: 1.7; color: #475569; margin: 0;">
                                Votre compte <strong>%s</strong> a été initialisé avec succès sur notre plateforme de gestion d'assiduité nouvelle génération.
                            </p>
                        </div>

                        <div style="background-color: #f8fafc; border-radius: 24px; padding: 24px; margin-bottom: 32px; border: 1px solid #e2e8f0;">
                            <div style="margin-bottom: 14px;">
                                <span style="color: #10b981; font-weight: 800; font-size: 12px; text-transform: uppercase; letter-spacing: 1px;">Au programme sur votre espace :</span>
                            </div>
                            <ul style="margin: 0; padding-left: 20px; color: #334155; font-size: 14px; line-height: 2.1;">
                                %s
                            </ul>
                        </div>

                        <div style="text-align: center; margin-bottom: 16px;">
                            <a href="http://localhost:3000/connexion" style="display: inline-block; background-color: #1a1c1e; color: #ffffff; padding: 18px 44px; border-radius: 24px; text-decoration: none; font-weight: 800; font-size: 15px; box-shadow: 0 10px 25px rgba(26,28,30,0.25);">Accéder à mon espace</a>
                        </div>

                        <div style="margin-top: 48px; padding-top: 24px; border-top: 1px solid #f1f5f9; text-align: center;">
                            <p style="color: #94a3b8; font-size: 11px; text-transform: uppercase; letter-spacing: 1.5px; margin: 0;">© 2026 UniCheck • Excellence Académique</p>
                        </div>

                    </div>
                </div>
            """.formatted(nomUtilisateur, role, specificDetails);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            System.out.println("🚀 Mail de bienvenue envoyé avec succès à " + toEmail + " (" + role + ")");
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'envoi du mail de bienvenue : " + e.getMessage());
        }
    }
}