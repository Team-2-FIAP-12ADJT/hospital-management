package com.fiap.hospital.notification.invite;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

final class MailFailure {

    private MailFailure() {}

    static boolean isTransient(MailException ex) {
        if (ex instanceof MailParseException || ex instanceof MailAuthenticationException) {
            return false;
        }
        if (isPermanent(ex)) {
            return false;
        }
        if (ex instanceof MailSendException send) {
            for (Exception nested : send.getMessageExceptions()) {
                if (isPermanent(nested)) {
                    return false;
                }
            }
        }
        if (isNetworkFailure(ex)) {
            return true;
        }
        return ex instanceof MailSendException;
    }

    private static boolean isPermanent(Throwable start) {
        for (Throwable cause = start; cause != null; cause = cause.getCause()) {
            if (cause instanceof AddressException || cause instanceof SendFailedException) {
                return true;
            }
            if (hasPermanentSmtpReply(cause.getMessage())) {
                return true;
            }
        }
        return false;
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

    private static boolean hasPermanentSmtpReply(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = " " + message.toLowerCase(Locale.ROOT) + " ";
        return normalized.contains(" 550 ")
            || normalized.contains(" 551 ")
            || normalized.contains(" 552 ")
            || normalized.contains(" 553 ")
            || normalized.contains(" 554 ")
            || normalized.contains(" 501 ");
    }
}
