package br.com.launchcontrol;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Cuida da proteção e da verificação de senhas. A senha não deve ser guardada diretamente no banco; guardamos um resumo criptográfico com salt.
 *
 * Uma classe é como uma caixa que reúne dados e ações relacionados a uma tarefa.
 */
public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private PasswordUtil() {}

    // Cria um hash de senha com salt aleatório; não guarda a senha original.
    public static String hash(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" +
                Base64.getEncoder().withoutPadding().encodeToString(salt) + "$" +
                Base64.getEncoder().withoutPadding().encodeToString(derived);
    }

    // Compara uma senha digitada com o hash salvo sem revelar a senha original.
    public static boolean verify(char[] password, String stored) throws GeneralSecurityException {
        if (stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
        int iterations = Integer.parseInt(parts[1]);
        if (iterations < 100_000 || iterations > 1_000_000) return false;
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = derive(password, salt, iterations);
        if (actual.length != expected.length) return false;
        int diff = 0;
        for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
        java.util.Arrays.fill(actual, (byte)0);
        return diff == 0;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
