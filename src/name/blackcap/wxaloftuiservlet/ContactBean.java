package name.blackcap.wxaloftuiservlet;

import com.sun.mail.smtp.SMTPTransport;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Message;
import javax.json.*;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Backs the contact.jsp page.
 *
 * @author David Barts <n5jrn@me.com>
 */
public class ContactBean
{
    private static final Logger LOGGER = Logger.getLogger(ObsDemoBean.class.getCanonicalName());
    private static final Pattern VALID_EMAIL = Pattern.compile("\\S+@\\S+\\.\\S+");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private boolean badAddress, missingAddress;
    private boolean missingMessage, badCaptcha;
    private String forward;

    public ContactBean()
    {
        badAddress = missingAddress = missingMessage = badCaptcha = false;
        forward = "";
    }

    public boolean processRequest(HttpServletRequest req, HttpServletResponse resp, ServletContext app) throws ServletException, IOException
    {
        /* if this is a non-POST request, it means we're displaying the
           form, not acting on any form data, in which case this bean is
           a no-op. */
        if (!"POST".equalsIgnoreCase(req.getMethod()))
            return true;

        /* validate email address */
        String address = req.getParameter("address");
        if (address == null || address.isEmpty())
            missingAddress = true;
        else
            badAddress = ! VALID_EMAIL.matcher(address).matches();

        /* validate message body */
        String message = req.getParameter("message");
        missingMessage = message == null || message.isEmpty();

        /* if a validation failed, we don't send an email */
        if (missingAddress || badAddress || missingMessage)
            return true;

        /* validate captcha; if bad, no send email for you! */
        try {
            badCaptcha = !validateCaptcha(
                app.getInitParameter("recaptcha.key.secret"),
                req.getParameter("g-recaptcha-response"), req.getRemoteAddr());
        } catch (IOException|JsonException e) {
            LOGGER.log(Level.SEVERE, "Unable to validate CAPTCHA", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Internal server error (unable to validata CAPTCHA).");
            return false;
        }
        if (badCaptcha)
            return true;

        /* build mailer properties */
        Properties props = new Properties();
        copyProp(props, app, "mail.smtps.host");
        copyProp(props, app, "mail.smtp.socketFactory.class");
        copyProp(props, app, "mail.smtp.socketFactory.fallback");
        copyProp(props, app, "mail.smtp.port");
        copyProp(props, app, "mail.smtp.socketFactory.port");
        copyProp(props, app, "mail.smtps.auth");

        /* get username, password, server, sender, recipient */
        String username = app.getInitParameter("mail.smtp.username");
        String password = app.getInitParameter("mail.smtp.password");
        String host = app.getInitParameter("mail.smtps.host");
        if (username == null || password == null || host == null) {
            LOGGER.log(Level.SEVERE, "Username, password, or host not defined");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Internal server error (username, password, or host not defined).");
            return false;
        }
        String sender = app.getInitParameter("mail.smtp.sender");
        if (sender == null)
            sender = username;
        String recipient = app.getInitParameter("mail.smtp.recipient");
        if (recipient == null) {
            LOGGER.log(Level.SEVERE, "Recipient not defined");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Internal server error (recipient not defined).");
            return false;
        }

        /* Get subject */
        String subject = req.getParameter("subject");
        if (subject == null || subject.isEmpty())
            subject = "KOOSAH: (no subject)";
        else
            subject = "KOOSAH: " + subject;

        /* send the message */
        try {
            Session session = Session.getInstance(props, null);
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(sender));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false));
            msg.setSubject(subject);
            msg.setText(message, "utf-8");
            msg.setSentDate(new Date());
            try (SMTPTransport t = (SMTPTransport)session.getTransport("smtps")) {
                t.connect(host, username, password);
                t.sendMessage(msg, msg.getAllRecipients());
            }
        } catch (MessagingException e) {
            forward = "sent.jsp?error=" + e.getMessage();
        }
        forward = "sent.jsp";

        return true;
    }

    private boolean validateCaptcha(String secret, String response, String ipaddr) throws IOException, JsonException
    {
        URL url = new URL("https://www.google.com/recaptcha/api/siteverify");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type",
            "application/x-www-form-urlencoded; charset=utf-8");
        String postData = "secret=" + secret + "&response=" + response +
            "&remoteip=" + ipaddr;
        try (OutputStream stream = conn.getOutputStream()) {
            stream.write(postData.getBytes(UTF8));
            stream.flush();
        }
        int status = conn.getResponseCode();
        if (!(status >= 200 && status <= 299)) {
            LOGGER.log(Level.SEVERE, String.format("Got %03d %s from Google",
                status, conn.getResponseMessage()));
            return false;
        }
        boolean ret = false;
        try (JsonReader jr = Json.createReader(conn.getInputStream())) {
            return jr.readObject().getBoolean("success", false);
        }
    }

    private void copyProp(Properties props, ServletContext app, String name)
    {
        props.setProperty(name, app.getInitParameter(name));
    }

    public boolean getBadAddress()
    {
        return badAddress;
    }

    public boolean getMissingAddress()
    {
        return missingAddress;
    }

    public boolean getMissingMessage()
    {
        return missingMessage;
    }

    public String getForward()
    {
        return forward;
    }

    public boolean getBadCaptcha()
    {
        return badCaptcha;
    }
}