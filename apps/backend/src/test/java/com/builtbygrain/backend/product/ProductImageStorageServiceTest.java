package com.builtbygrain.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.builtbygrain.backend.storage.ObjectStorage;
import com.builtbygrain.backend.storage.ObjectStorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ProductImageStorageServiceTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final ProductImageStorageService images = new ProductImageStorageService(storage, 64);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void detectsSignaturesAndRejectsDeclaredTypeMismatchesAndOversizedBytes() {
        MockMultipartFile png = new MockMultipartFile("image", "photo.png", "image/png", TestImages.png());
        assertThat(images.store(png)).matches("/api/public/uploads/products/[0-9a-f-]{36}\\.png");
        verify(storage).put(anyString(), eq(TestImages.png()), eq("image/png"), anyString());

        MockMultipartFile mismatch = new MockMultipartFile("image", "photo.jpg", "image/jpeg", TestImages.png());
        assertThatThrownBy(() -> images.store(mismatch))
            .isInstanceOf(ProductImageStorageException.class)
            .hasMessageContaining("does not match");

        MockMultipartFile oversized = new MockMultipartFile("image", "large.png", "image/png", new byte[65]);
        assertThatThrownBy(() -> images.store(oversized))
            .isInstanceOf(ProductImageStorageException.class)
            .hasMessageContaining("too large");
    }

    @Test
    void acceptsEverySupportedImageSignatureAndUsesServerOwnedExtensions() {
        assertThat(images.store(new MockMultipartFile(
            "image", "client-name.jpeg", "image/jpeg", TestImages.jpeg()
        ))).matches("/api/public/uploads/products/[0-9a-f-]{36}\\.jpg");
        assertThat(images.store(new MockMultipartFile(
            "image", "client-name.gif", "image/gif", TestImages.gif()
        ))).matches("/api/public/uploads/products/[0-9a-f-]{36}\\.gif");
        assertThat(images.store(new MockMultipartFile(
            "image", "client-name.webp", "image/webp", TestImages.webp()
        ))).matches("/api/public/uploads/products/[0-9a-f-]{36}\\.webp");
        verify(storage).put(anyString(), eq(TestImages.jpeg()), eq("image/jpeg"), anyString());
        verify(storage).put(anyString(), eq(TestImages.gif()), eq("image/gif"), anyString());
        verify(storage).put(anyString(), eq(TestImages.webp()), eq("image/webp"), anyString());
    }

    @Test
    void removesNewObjectsOnRollbackAndOldObjectsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        String newUrl = images.store(new MockMultipartFile("image", "photo.png", "image/png", TestImages.png()));
        String newKey = newUrl.substring("/api/public/uploads/".length());
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete(newKey);
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        String oldKey = "products/00000000-0000-0000-0000-000000000000.png";
        images.delete("/api/public/uploads/" + oldKey);
        verify(storage, never()).delete(oldKey);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete(oldKey);
    }

    @Test
    void cleanupFailureAfterCommitDoesNotTurnACommittedMutationIntoAnError() {
        String key = "products/00000000-0000-0000-0000-000000000001.png";
        doThrow(new ObjectStorageException("unavailable", false)).when(storage).delete(key);
        TransactionSynchronizationManager.initSynchronization();
        images.delete("/api/public/uploads/" + key);

        assertThatNoException().isThrownBy(() -> TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit));
    }
}
