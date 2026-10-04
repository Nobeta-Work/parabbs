package cn.nobeta.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Reads existing BBS hashes; all new credentials use Spring's algorithm-prefixed format. */
public final class MigratingPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override public String encode(CharSequence raw) { return delegate.encode(raw); }

    @Override public boolean matches(CharSequence raw, String encoded) {
        if (raw == null || encoded == null) return false;
        try {
            if (encoded.startsWith("{")) return delegate.matches(raw, encoded);
            if (encoded.startsWith("$2")) return bcrypt.matches(raw, encoded);
            byte[] value = Base64.getDecoder().decode(encoded);
            if (value.length != 48) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Arrays.copyOfRange(value, 0, 16));
            byte[] actual = digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(actual, Arrays.copyOfRange(value, 16, 48));
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override public boolean upgradeEncoding(String encoded) {
        return encoded != null && (!encoded.startsWith("{") || delegate.upgradeEncoding(encoded));
    }
}
