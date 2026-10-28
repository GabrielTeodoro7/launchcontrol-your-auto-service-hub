package br.com.launchcontrol;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Implementa a geração e a verificação de hashes de senha com salt.
 *
 */
public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private PasswordUtil() {}

    // Gera um hash usando um salt aleatório.
    public static String hash(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        try {
            return "pbkdf2-sha256$" + ITERATIONS + "$" +
                    Base64.getEncoder().withoutPadding().encodeToString(salt) + "$" +
                    Base64.getEncoder().withoutPadding().encodeToString(derived);
        } finally {
            java.util.Arrays.fill(derived, (byte) 0);
        }
    }

    // Verifica a senha informada em relação ao hash armazenado.
    public static boolean verify(char[] password, String stored) throws GeneralSecurityException {
        if (stored == null) return false;
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            try {
                if (actual.length != expected.length) return false;
                int diff = 0;
                for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
                return diff == 0;
            } finally {
                java.util.Arrays.fill(actual, (byte) 0);
                java.util.Arrays.fill(expected, (byte) 0);
                java.util.Arrays.fill(salt, (byte) 0);
            }
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    // Deriva o hash com PBKDF2, salt e fator de trabalho configurado.
    private static byte[] derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
