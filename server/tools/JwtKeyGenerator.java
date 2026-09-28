import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class JwtKeyGenerator {
    private JwtKeyGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDirectory = args.length == 0
                ? Path.of("src", "main", "resources")
                : Path.of(args[0]);

        Files.createDirectories(outputDirectory);

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        writePem(
                outputDirectory.resolve("privateKey.pem"),
                "PRIVATE KEY",
                keyPair.getPrivate().getEncoded()
        );

        writePem(
                outputDirectory.resolve("publicKey.pem"),
                "PUBLIC KEY",
                keyPair.getPublic().getEncoded()
        );

        System.out.println(
                "JWT keys created in: "
                        + outputDirectory.toAbsolutePath()
        );
    }

    private static void writePem(
            Path destination,
            String type,
            byte[] encoded
    ) throws Exception {
        String base64 = Base64.getMimeEncoder(
                64,
                System.lineSeparator()
                        .getBytes(StandardCharsets.UTF_8)
        ).encodeToString(encoded);

        String pem = """
                -----BEGIN %s-----
                %s
                -----END %s-----
                """.formatted(type, base64, type);

        Files.writeString(
                destination,
                pem,
                StandardCharsets.UTF_8
        );
    }

}
