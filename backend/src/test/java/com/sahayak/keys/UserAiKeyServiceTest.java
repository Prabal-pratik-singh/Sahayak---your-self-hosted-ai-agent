package com.sahayak.keys;

import com.sahayak.agent.ChatClientFactory;
import com.sahayak.common.AppSecret;
import com.sahayak.common.CryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAiKeyServiceTest {

    private final UserAiKeyRepository repository = mock(UserAiKeyRepository.class);
    private final CryptoService crypto = new CryptoService(new AppSecret("unit-test-secret"));
    private final ChatClientFactory factory = mock(ChatClientFactory.class);
    private final UserAiKeyService service = new UserAiKeyService(repository, crypto, factory);

    @Test
    void savedKeysAreEncryptedAtRest() {
        when(factory.known("gemini")).thenReturn(true);
        when(factory.labelOf("gemini")).thenReturn("Gemini");
        // verification client blows up with an unknown error → key still saved with warning
        when(factory.createBare(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        when(repository.findByUserIdAndProvider(4L, "gemini")).thenReturn(Optional.empty());

        var result = service.save(4L, "gemini", "  my-secret-key  ");

        ArgumentCaptor<UserAiKey> captor = ArgumentCaptor.forClass(UserAiKey.class);
        verify(repository).save(captor.capture());
        UserAiKey saved = captor.getValue();

        assertEquals(4L, saved.getUserId());
        assertNotEquals("my-secret-key", saved.getEncryptedKey());
        assertEquals("my-secret-key", crypto.decrypt(saved.getEncryptedKey()));
        assertEquals("saved", result.status());
        assertTrue(result.warning() != null && result.warning().contains("could not be verified"), String.valueOf(result.warning()));
    }

    @Test
    void keysRejectedByTheProviderAreNotSaved() {
        when(factory.known("openai")).thenReturn(true);
        when(factory.labelOf("openai")).thenReturn("ChatGPT");
        when(factory.createBare(anyString(), anyString()))
                .thenThrow(new RuntimeException("401 Unauthorized - incorrect API key provided"));

        var e = assertThrows(ResponseStatusException.class, () -> service.save(4L, "openai", "bad-key"));

        assertEquals(400, e.getStatusCode().value());
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void unknownProviderIsRejected() {
        when(factory.known("skynet")).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.save(4L, "skynet", "key"));
    }
}
