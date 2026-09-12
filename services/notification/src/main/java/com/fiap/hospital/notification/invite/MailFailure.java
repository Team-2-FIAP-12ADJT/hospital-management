package com.fiap.hospital.notification.invite;

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

final class MailFailure {

    // Matches a 3-digit SMTP reply code only at the start of the message or at the
    // start of a line within it (Pattern.MULTILINE ^), which is where a real SMTP
    // reply code appears — never as a substring anywhere else in the text (e.g. a
    // hostname, a port number, or "on port 465" trailing a bounce message).
    private static final Pattern SMTP_REPLY_CODE = Pattern.compile("(?m)^([2-5]\\d{2})(?=[\\s-]|$)");

    private MailFailure() {}

    static boolean isTransient(MailException ex) {
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
     * unclassifiable by this method (null). Rules, in precedence order:
     * (a) an AddressException anywhere in the cause chain is always permanent;
     * (b) a SendFailedException with at least one invalid address is permanent;
     * (c) a 4xx SMTP reply code anywhere in the chain is transient, and this
     *     takes precedence over (d);
     * (d) a 5xx SMTP reply code anywhere in the chain is permanent;
     * (e) a SendFailedException with no recognizable reply code and no invalid
     *     address is transient, since retrying is the safe default.
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
