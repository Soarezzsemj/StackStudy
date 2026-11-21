package com.stackstudy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackstudy.account.photo.ProfilePhotoProcessor;
import com.stackstudy.auth.google.GoogleIdTokenVerifier;
import com.stackstudy.auth.google.GoogleIdTokenVerifier.InvalidGoogleTokenException;
import com.stackstudy.common.error.ApiException;
import io.jsonwebtoken.Jwts;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SignupUnitTest {

  // senha ------------------------------------------------------------------------------

  @Test
  void passwordPolicyRejectsShortCommonAndEmailLikePasswords() {
    assertThatThrownBy(() -> PasswordPolicy.check("password", "123456789", null))
        .isInstanceOf(ApiException.class);
    for (String common :
        new String[] {"1234567890", "QWERTYUIOP", " senha12345 ", "zzzzzzzzzzzz", "ababababab"}) {
      assertThatThrownBy(() -> PasswordPolicy.check("password", common, null))
          .as(common)
          .isInstanceOf(ApiException.class);
    }
    assertThatThrownBy(() -> PasswordPolicy.check("password", "joana.silva", "Joana.Silva@uni.br"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> PasswordPolicy.check("password", "x".repeat(73), null))
        .isInstanceOf(ApiException.class);
    assertThatCode(() -> PasswordPolicy.check("password", "correct horse battery", "a@b.c"))
        .doesNotThrowAnyException();
  }

  // foto -------------------------------------------------------------------------------

  static byte[] jpeg(int w, int h) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", out);
    return out.toByteArray();
  }

  /** JPEG com um segmento APP1 "Exif" falso (como o GPS de uma foto de celular). */
  static byte[] withExif(byte[] jpeg, String secret) {
    byte[] payload = ("Exif\0\0" + secret).getBytes(StandardCharsets.ISO_8859_1);
    int len = payload.length + 2;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xFF);
    out.write(0xE1);
    out.write(len >> 8);
    out.write(len & 0xFF);
    out.write(payload, 0, payload.length);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  @Test
  void photosAreCheckedByContentAndReencodedWithoutMetadata() throws Exception {
    byte[] original = withExif(jpeg(800, 400), "GPS-SECRET-LOCATION");
    assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("GPS-SECRET-LOCATION");
    assertThat(ProfilePhotoProcessor.detect(original)).isEqualTo(ProfilePhotoProcessor.Kind.JPEG);

    byte[] out = ProfilePhotoProcessor.process(original);
    assertThat(ProfilePhotoProcessor.detect(out)).isEqualTo(ProfilePhotoProcessor.Kind.PNG);
    assertThat(new String(out, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRET");
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(out));
    assertThat(img.getWidth()).isEqualTo(256);
    assertThat(img.getHeight()).isEqualTo(128);
  }

  @Test
  void fakeOrUnsupportedImagesAreRejected() {
    // "foto.png" que na verdade é texto (ou HTML): recusado pelo conteúdo.
    byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
    assertThat(ProfilePhotoProcessor.detect(html)).isNull();
    assertThatThrownBy(() -> ProfilePhotoProcessor.process(html)).isInstanceOf(ApiException.class);
    byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    assertThat(ProfilePhotoProcessor.detect(webp)).isEqualTo(ProfilePhotoProcessor.Kind.WEBP);
    assertThatThrownBy(() -> ProfilePhotoProcessor.process(webp)).isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> ProfilePhotoProcessor.process(new byte[ProfilePhotoProcessor.MAX_BYTES + 1]))
        .isInstanceOf(ApiException.class);
  }

  // Google -----------------------------------------------------------------------------

  static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
  static final KeyPair KEYS = Jwts.SIG.RS256.keyPair().build();

  static String token(String iss, String aud, String nonce, Object emailVerified, Instant exp) {
    return Jwts.builder()
        .header()
        .keyId("k1")
        .and()
        .issuer(iss)
        .audience()
        .add(aud)
        .and()
        .subject("google-123")
        .claim("email", "ana@example.com")
        .claim("email_verified", emailVerified)
        .claim("nonce", nonce)
        .issuedAt(Date.from(NOW.minusSeconds(60)))
        .expiration(Date.from(exp))
        .signWith(KEYS.getPrivate())
        .compact();
  }

  static GoogleIdTokenVerifier verifier() {
    return new GoogleIdTokenVerifier(
        "client-1",
        kid -> "k1".equals(kid) ? KEYS.getPublic() : null,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void googleIdTokensAreCheckedForSignatureIssuerAudienceExpiryAndNonce() {
    Instant later = NOW.plus(Duration.ofMinutes(5));
    GoogleIdTokenVerifier.GoogleIdentity ok =
        verifier()
            .verify(token("https://accounts.google.com", "client-1", "n1", true, later), "n1");
    assertThat(ok.sub()).isEqualTo("google-123");
    assertThat(ok.emailVerified()).isTrue();
    assertThat(
            verifier()
                .verify(token("accounts.google.com", "client-1", "n1", "false", later), "n1")
                .emailVerified())
        .isFalse();

    assertThatThrownBy(
            () ->
                verifier()
                    .verify(token("https://evil.example", "client-1", "n1", true, later), "n1"))
        .isInstanceOf(InvalidGoogleTokenException.class);
    assertThatThrownBy(
            () ->
                verifier()
                    .verify(token("accounts.google.com", "other-client", "n1", true, later), "n1"))
        .isInstanceOf(InvalidGoogleTokenException.class);
    assertThatThrownBy(
            () ->
                verifier()
                    .verify(token("accounts.google.com", "client-1", "n1", true, later), "n2"))
        .isInstanceOf(InvalidGoogleTokenException.class);
    assertThatThrownBy(
            () ->
                verifier()
                    .verify(
                        token("accounts.google.com", "client-1", "n1", true, NOW.minusSeconds(600)),
                        "n1"))
        .isInstanceOf(InvalidGoogleTokenException.class);
    // Assinado por outra chave.
    KeyPair other = Jwts.SIG.RS256.keyPair().build();
    String forged =
        Jwts.builder()
            .header()
            .keyId("k1")
            .and()
            .issuer("accounts.google.com")
            .audience()
            .add("client-1")
            .and()
            .subject("x")
            .claim("email", "a@b.c")
            .claim("nonce", "n1")
            .expiration(Date.from(later))
            .signWith(other.getPrivate())
            .compact();
    assertThatThrownBy(() -> verifier().verify(forged, "n1"))
        .isInstanceOf(InvalidGoogleTokenException.class);
  }
}
