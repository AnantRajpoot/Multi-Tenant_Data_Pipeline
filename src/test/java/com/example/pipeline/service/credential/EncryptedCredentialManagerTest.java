package com.example.pipeline.service.credential;

import com.example.pipeline.model.Credential;
import com.example.pipeline.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EncryptedCredentialManager.
 */
@DataJpaTest
@Import(EncryptedCredentialManager.class)
class EncryptedCredentialManagerTest {
    
    @Autowired
    private CredentialRepository credentialRepository;
    
    @Autowired
    private EncryptedCredentialManager credentialManager;
    
    private static final String TEST_PASSWORD = "test-encryption-password";
    
    @BeforeEach
    void setUp() {
        credentialRepository.deleteAll();
    }
    
    @Test
    void testStoreAndRetrieveCredential() {
        // Store credential
        String credName = "test-s3-key";
        String credValue = "super-secret-access-key-12345";
        credentialManager.storeCredential(credName, "s3", credValue, "Test S3 credential");
        
        // Retrieve credential
        String retrieved = credentialManager.getCredential(credName).orElse(null);
        assertNotNull(retrieved);
        assertEquals(credValue, retrieved);
    }
    
    @Test
    void testStoredValueIsEncrypted() {
        // Store credential
        String credName = "test-db-password";
        String credValue = "my-super-secret-password";
        credentialManager.storeCredential(credName, "database", credValue, "Test DB password");
        
        // Verify stored value is encrypted (not plaintext)
        Credential stored = credentialRepository.findByName(credName).orElse(null);
        assertNotNull(stored);
        assertNotEquals(credValue, stored.getEncryptedValue());
        assertTrue(stored.getEncryptedValue().length() > 0);
    }
    
    @Test
    void testUpdateCredential() {
        // Store initial credential
        credentialManager.storeCredential("test-api-key", "api", "old-value", "Test API key");
        
        // Update with new value
        String newValue = "new-updated-value";
        credentialManager.updateCredential("test-api-key", newValue);
        
        // Verify updated
        String retrieved = credentialManager.getCredential("test-api-key").orElse(null);
        assertEquals(newValue, retrieved);
    }
    
    @Test
    void testDeleteCredential() {
        // Store credential
        credentialManager.storeCredential("test-to-delete", "s3", "value-to-delete", "Will be deleted");
        assertTrue(credentialManager.credentialExists("test-to-delete"));
        
        // Delete
        credentialManager.deleteCredential("test-to-delete");
        assertFalse(credentialManager.credentialExists("test-to-delete"));
    }
    
    @Test
    void testCredentialExists() {
        String name = "test-exists";
        assertFalse(credentialManager.credentialExists(name));
        
        credentialManager.storeCredential(name, "api", "value", "Description");
        assertTrue(credentialManager.credentialExists(name));
    }
    
    @Test
    void testDuplicateCredentialName() {
        credentialManager.storeCredential("duplicate", "s3", "value1", "First");
        
        assertThrows(IllegalArgumentException.class, () -> {
            credentialManager.storeCredential("duplicate", "s3", "value2", "Second");
        });
    }
    
    @Test
    void testListCredentialNames() {
        credentialManager.storeCredential("cred1", "s3", "val1", "Desc1");
        credentialManager.storeCredential("cred2", "database", "val2", "Desc2");
        credentialManager.storeCredential("cred3", "api", "val3", "Desc3");
        
        var names = credentialManager.listCredentialNames();
        assertEquals(3, names.size());
        assertTrue(names.contains("cred1"));
        assertTrue(names.contains("cred2"));
        assertTrue(names.contains("cred3"));
    }
}
