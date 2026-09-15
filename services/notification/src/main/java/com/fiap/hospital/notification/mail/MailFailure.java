package com.fiap.hospital.notification.mail;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

public final class MailFailure {

    private static final Pattern SMTP_REPLY_CODE = Pattern.compile("(?m)^([2-5]\\d{2})(?=[\\s-]|$)");

    private MailFailure() {}

    /**
     * Descreve a falha para log sem repetir a mensagem do servidor: ela carrega o
     * endereço do destinatário numa rejeição típica ({@code 550 <email> rejected}).
     * Sobra o que serve para diagnóstico — a causa mais específica e, quando existe,
     * o código de resposta SMTP, que é o que separa 4xx de 5xx.
     */
    public static String describe(MailException ex) {
        Throwable specific = ex;
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            specific = cause;
        }
        String replyCode = firstReplyCode(ex);
        String kind = specific.getClass().getName();
        return replyCode == null ? kind : kind + " smtpReply=" + replyCode;
    }

    private static String firstReplyCode(MailException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            Matcher matcher = SMTP_REPLY_CODE.matcher(cause.getMessage() == null ? "" : cause.getMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (ex instanceof MailSendException send) {
            for (Exception nested : send.getMessageExceptions()) {
                for (Throwable cause = nested; cause != null; cause = cause.getCause()) {
                    Matcher matcher = SMTP_REPLY_CODE.matcher(cause.getMessage() == null ? "" : cause.getMessage());
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        }
        return null;
    }

    public static boolean isTransient(MailException ex) {
        if (ex instanceof MailParseException || ex instanceof MailAuthenticationException) {
            return false;
        }
        Boolean permanent = classify(ex);
        if (permanent != null) {
            return !permanent;
        }
        if (ex instanceof MailSendException send) {
            for (Exception nested : send.getMessageExceptions()) {
                Boolean nestedPermanent = classify(nested);
                if (nestedPermanent != null) {
                    return !nestedPermanent;
                }
            }
        }
        if (isNetworkFailure(ex)) {
            return true;
        }
        return ex instanceof MailSendException;
    }

    /**
     * Classifies an SMTP failure as permanent (true), transient (false), or
     * unclassifiable by this method (null).
     */
    private static Boolean classify(Throwable start) {
        for (Throwable cause = start; cause != null; cause = cause.getCause()) {
            if (cause instanceof AddressException) {
                return true;
            }
        }
        for (Throwable cause = start; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException sendFailed && hasInvalidAddress(sendFailed)) {
                return true;
            }
        }
        boolean sawTransientReply = false;
        boolean sawPermanentReply = false;
        boolean sawSendFailedException = false;
        for (Throwable cause = start; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException) {
                sawSendFailedException = true;
            }
            Matcher matcher = SMTP_REPLY_CODE.matcher(cause.getMessage() == null ? "" : cause.getMessage());
            while (matcher.find()) {
                char leadingDigit = matcher.group(1).charAt(0);
                if (leadingDigit == '4') {
                    sawTransientReply = true;
                } else if (leadingDigit == '5') {
                    sawPermanentReply = true;
                }
            }
        }
        if (sawTransientReply) {
            return false;
        }
        if (sawPermanentReply) {
            return true;
        }
        if (sawSendFailedException) {
            return false;
        }
        return null;
    }

    private static boolean hasInvalidAddress(SendFailedException ex) {
        return ex.getInvalidAddresses() != null && ex.getInvalidAddresses().length > 0;
    }

    private static boolean isNetworkFailure(Throwable start) {
        for (Throwable cause = start; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException
                || cause instanceof SocketTimeoutException
                || cause instanceof UnknownHostException
                || cause instanceof SocketException) {
                return true;
            }
        }
        return false;
    }
}
