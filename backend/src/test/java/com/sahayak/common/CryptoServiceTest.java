package com.sahayak.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService(new AppSecret("unit-test-secret"));

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String plain = "{\"password\":\"very secret\"}";
        String encrypted = crypto.encrypt(plain);

        assertNotEquals(plain, encrypted);
        assertEquals(plain, crypto.decrypt(encrypted));
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        String plain = "hello";
        assertNotEquals(crypto.encrypt(plain), crypto.encrypt(plain));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String encrypted = crypto.encrypt("hello");
        char flipped = encrypted.charAt(10) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, 10) + flipped + encrypted.substring(11);

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    void differentSecretCannotDecrypt() {
        String encrypted = crypto.encrypt("hello");
        CryptoService other = new CryptoService(new AppSecret("another-secret"));

        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }
}
