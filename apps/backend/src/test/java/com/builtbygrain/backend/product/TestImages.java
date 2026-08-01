package com.builtbygrain.backend.product;

public final class TestImages {

    private TestImages() { }

    public static byte[] png() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x00
        };
    }

    public static byte[] jpeg() {
        return new byte[] {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0,
            0x00, 0x00, (byte) 0xff, (byte) 0xd9
        };
    }

    public static byte[] gif() {
        return new byte[] {'G', 'I', 'F', '8', '9', 'a', 0x00, 0x00};
    }

    public static byte[] webp() {
        return new byte[] {'R', 'I', 'F', 'F', 0x04, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
    }
}
