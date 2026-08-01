package com.builtbygrain.backend.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicUploadControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectStorage storage;

    private String filename;
    private byte[] bytes;

    @BeforeEach
    void storeObject() throws Exception {
        filename = UUID.randomUUID() + ".png";
        bytes = "test-image-payload".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        storage.put("products/" + filename, bytes, "image/png", digest);
    }

    @Test
    void servesGetHeadRangeAndConditionalRequestsWithImmutableHeaders() throws Exception {
        String path = "/api/public/uploads/products/" + filename;
        String etag = mvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().bytes(bytes))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mvc.perform(head(path))
            .andExpect(status().isOk())
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.length));
        mvc.perform(get(path).header(HttpHeaders.RANGE, "bytes=0-3"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/" + bytes.length))
            .andExpect(content().bytes(java.util.Arrays.copyOfRange(bytes, 0, 4)));
        mvc.perform(get(path).header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified());
    }

    @Test
    void rejectsUnknownNamespacesMalformedKeysAndMissingObjects() throws Exception {
        mvc.perform(get("/api/public/uploads/private/" + filename)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/uploads/products/not-a-managed-key.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/uploads/products/" + UUID.randomUUID() + ".png")).andExpect(status().isNotFound());
    }
}
