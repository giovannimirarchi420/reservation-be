package it.polito.cloudresources.be.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;

/**
 * Utility class for SSH key validation
 */
@Component
public class SshKeyValidator {
    
    // Regex pattern per le chiavi SSH
    private static final Pattern SSH_KEY_PATTERN = Pattern.compile("^(ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp256|ecdsa-sha2-nistp384|ecdsa-sha2-nistp521)\\s+([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?\\s*(.*)?$");
    
    /**
     * Validates an SSH public key format
     * 
     * @param sshKey the SSH key to validate
     * @return true if the key is valid, false otherwise
     */
    public void isValidSshPublicKey(String sshKey) {
        if (sshKey == null || sshKey.trim().isEmpty() || !SSH_KEY_PATTERN.matcher(sshKey.trim()).matches()) {
            throw new IllegalArgumentException("SSH key not valid");
        }
    }
    
    /**
     * Formats an SSH key by trimming and removing extra whitespaces
     * 
     * @param sshKey the SSH key to format
     * @return the formatted SSH key
     */
    public String formatSshKey(String sshKey) {
        if (sshKey == null) {
            return null;
        }
        
        // Remove all line breaks and extra spaces
        return sshKey.trim().replaceAll("\\s+", " ");
    }
}