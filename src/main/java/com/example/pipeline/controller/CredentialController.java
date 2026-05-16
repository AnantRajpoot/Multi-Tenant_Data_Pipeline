package com.example.pipeline.controller;

import com.example.pipeline.model.Credential;
import com.example.pipeline.repository.CredentialRepository;
import com.example.pipeline.service.credential.EncryptedCredentialManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/credentials")
public class CredentialController {
    @Autowired
    private EncryptedCredentialManager credentialManager;
    @Autowired
    private CredentialRepository credentialRepository;

    @PostMapping
    public Credential addCredential(@RequestBody CredentialRequest req) {
        return credentialManager.storeCredential(req.name, req.type, req.value, req.description);
    }

    @GetMapping("/{name}")
    public Optional<String> getCredential(@PathVariable String name) {
        return credentialManager.getCredential(name);
    }

    @PutMapping("/{name}")
    public void updateCredential(@PathVariable String name, @RequestBody UpdateRequest req) {
        credentialManager.updateCredential(name, req.value);
    }

    @DeleteMapping("/{name}")
    public void deleteCredential(@PathVariable String name) {
        credentialManager.deleteCredential(name);
    }

    @GetMapping
    public List<String> listCredentialNames() {
        return credentialManager.listCredentialNames();
    }

    // DTOs for requests
    public static class CredentialRequest {
        public String name;
        public String type;
        public String value;
        public String description;
    }
    public static class UpdateRequest {
        public String value;
    }
}
