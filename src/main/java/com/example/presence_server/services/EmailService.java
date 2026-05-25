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

    private final String EXPEDITEUR_AUTORISE = "unickeckalerte@gmail.com";

    // Logo compact : carré vert avec point blanc + texte UniCheck QR (UniCheck en noir)
    private String getLogoHtml() {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 14px; text-align:center;">
              <tr>
                <td style="vertical-align:middle;">
                  <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto;">
                    <tr>
                      <td style="vertical-align:middle; padding-right:10px;">
                        <!-- carré vert avec point blanc -->
                        <div style="width:36px; height:36px; background:#006c49; border-radius:10px; display:inline-block; vertical-align:middle; text-align:center; line-height:36px;">
                          <div style="width:6px; height:6px; background:#ffffff; border-radius:50%; margin:0 auto;"></div>
                        </div>
                      </td>
                      <td style="vertical-align:middle;">
                        <!-- texte du logo : UniCheck (noir) + QR -->
                        <div style="font-family:Manrope, 'Outfit', Inter, -apple-system, sans-serif; font-weight:800; font-size:18px; color:#0f172a; line-height:1;">
                          <span style="color:#0f172a;">UniCheck</span><span style="color:#0f172a; font-weight:900;"> QR</span>
                        </div>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
        """;
    }

    // Wrapper commun (table centré, card blanche, font stack + fallbacks)
    private String wrapHtml(String content) {
        return """
            <!doctype html>
            <html lang="fr">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>UniCheck QR</title>
              <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@700;800;900&family=Inter:wght@300;400;600&family=Noto+Sans+Arabic:wght@400;700&display=swap" rel="stylesheet">
            </head>
            <body style="margin:0; padding:0; background-color:#f1f5f9; -webkit-font-smoothing:antialiased;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:36px 16px; background:#f1f5f9;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:620px; border-radius:20px; overflow:hidden; background:#ffffff; border:1px solid #e6eef6; box-shadow:0 30px 60px rgba(2,6,23,0.06);">
                      <tr>
                        <td style="padding:36px 36px 18px; text-align:center;">
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:0 36px 36px;">
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="background:#f8fafc; padding:18px 36px; text-align:center; border-top:1px solid #e6eef6;">
                          <p style="margin:0; font-family:Inter, Arial, sans-serif; color:#64748b; font-size:12px; font-weight:600;">
                            © 2026 UniCheck QR • Excellence Académique
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
        """.formatted(getLogoHtml(), content);
    }

    // CTA stylisé (arrondi, ombre légère)
    private String btnHtml(String label, String url) {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:22px auto 0; text-align:center;">
              <tr>
                <td align="center">
                  <a href="%s" style="display:inline-block; padding:12px 30px; background:#0f172a; color:#ffffff; text-decoration:none; border-radius:999px; font-weight:700; font-family:Manrope, Inter, Arial, sans-serif; font-size:15px; box-shadow:0 6px 18px rgba(15,23,42,0.12);">
                    %s
                  </a>
                </td>
              </tr>
            </table>
        """.formatted(url, label);
    }

    // Helper: bande d'état (alert / success / info)
    private String statusPill(String text, String bg, String color) {
        return """
            <div style="display:inline-block; padding:6px 10px; border-radius:999px; background:%s; color:%s; font-weight:800; font-size:11px; font-family:Inter, Arial, sans-serif; letter-spacing:0.6px;">
              %s
            </div>
        """.formatted(bg, color, text);
    }

    // 1. ALERTE ABSENCE
    public void envoyerAlerteAbsence(String toEmail, String nomEtudiant,
                                     String module, String nomProf, long nbAbsences) {
        System.out.println("📧 [EMAIL] Tentative envoi alerte absence → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck — Alerte assiduité : " + module);

            String header = statusPill("Seuil critique", "#fff1f2", "#dc2626");

            String body = """
                <div style="text-align:center; margin-bottom:18px;">%s</div>

                <h2 style="font-family:Manrope, Inter, Arial, sans-serif; color:#0f172a; font-size:22px; margin:0 0 12px; font-weight:800; text-align:center;">
                  Bonjour %s,
                </h2>

                <p style="font-family:Inter, Arial, sans-serif; color:#475569; font-size:15px; line-height:1.6; text-align:center; margin:0 0 18px;">
                  Le système a détecté un taux d'absence élevé pour le module
                  <strong style="color:#0f172a; font-weight:800;">%s</strong> (enseigné par %s).
                </p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:18px 0 18px;">
                  <tr>
                    <td align="center">
                      <div style="display:inline-block; background:#fff7f8; border-radius:14px; padding:18px 22px; border:1px solid #fde2e6; text-align:center; min-width:160px;">
                        <div style="font-family:Manrope, Inter, Arial, sans-serif; font-size:12px; color:#64748b; font-weight:700; text-transform:uppercase; letter-spacing:1px; margin-bottom:6px;">
                          Absences cumulées
                        </div>
                        <div style="font-family:Manrope, Inter, Arial, sans-serif; font-size:40px; color:#dc2626; font-weight:900; line-height:1;">
                          %d
                        </div>
                      </div>
                    </td>
                  </tr>
                </table>

                <p style="font-family:Inter, Arial, sans-serif; color:#475569; font-size:14px; line-height:1.6; text-align:center; margin:0;">
                  Dépasser le quota peut compromettre la validation du semestre. Pensez à déposer vos justificatifs rapidement.
                </p>
                """.formatted(header, nomEtudiant, module, nomProf, nbAbsences);

            body += btnHtml("Déposer un justificatif", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Alerte absence envoyée → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec alerte absence → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }

    // 2. BIENVENUE (simplifié : message unique, pas de listes d'icônes)
    public void envoyerMailBienvenue(String toEmail, String nomUtilisateur, String role) {
        System.out.println("📧 [EMAIL] Tentative envoi bienvenue → " + toEmail + " (" + role + ")");
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("🚀 Bienvenue sur UniCheck !");

            String header = statusPill("Espace activé", "#ecfdf5", "#059669");

            String body = """
                <div style="text-align:center; margin-bottom:14px;">%s</div>

                <h2 style="font-family:Manrope, Inter, Arial, sans-serif; color:#0f172a; font-size:22px; margin:0 0 10px; font-weight:800; text-align:center;">
                  Bienvenue %s,
                </h2>

                <p style="font-family:Inter, Arial, sans-serif; color:#475569; font-size:15px; line-height:1.6; text-align:center; margin:0 0 16px;">
                  Votre compte est désormais actif. Vous pouvez vous connecter pour accéder à votre espace et commencer à utiliser UniCheck QR.
                </p>
                """.formatted(header, nomUtilisateur);

            body += btnHtml("Accéder à mon espace", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Bienvenue envoyé → " + toEmail);

        } catch (Exception e) {
            System.err.println("❌ [EMAIL] Échec bienvenue → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }

    // 3. NOTIFICATION PLANNING
    public void envoyerNotifPlanning(String toEmail, String nomDestinataire,
                                     String sujet, String contenu) {
        System.out.println("📧 [EMAIL] Tentative envoi planning → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("📅 UniCheck — " + sujet);

            String header = statusPill("Mise à jour planning", "#eff6ff", "#2563eb");

            String body = """
                <div style="text-align:center; margin-bottom:14px;">%s</div>

                <h2 style="font-family:Manrope, Inter, Arial, sans-serif; color:#0f172a; font-size:22px; margin:0 0 10px; font-weight:800; text-align:center;">
                  Bonjour %s,
                </h2>

                <p style="font-family:Inter, Arial, sans-serif; color:#475569; font-size:15px; line-height:1.6; text-align:center; margin:0 0 16px;">
                  %s
                </p>

                <div style="background:#f8fafc; border-radius:12px; padding:14px; border:1px solid #e6eef6; text-align:center; margin-bottom:12px;">
                  <p style="margin:0; font-size:14px; color:#475569; font-weight:600;">
                    Connectez-vous pour voir les modifications en détail.
                  </p>
                </div>
                """.formatted(header, nomDestinataire, contenu);

            body += btnHtml("Voir mon planning", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Planning envoyé → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec planning → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }
}
