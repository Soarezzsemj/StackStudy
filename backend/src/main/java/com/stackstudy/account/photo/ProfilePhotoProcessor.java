package com.stackstudy.account.photo;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Foto de perfil (puro, testado): o tipo é conferido pelo CONTEÚDO (assinatura do arquivo), nunca
 * pela extensão ou pelo Content-Type. A imagem é decodificada e regravada do zero como PNG de até
 * 256×256: EXIF, GPS e qualquer outro metadado somem, e o arquivo original nunca é servido.
 *
 * <p>JPEG e PNG são decodificados pelo Java. WebP é reconhecido, mas o Java não traz decodificador;
 * fica recusado (UNSUPPORTED_IMAGE) até entrar uma biblioteca para isso.
 */
public final class ProfilePhotoProcessor {

  public static final int MAX_BYTES = 2 * 1024 * 1024;
  public static final int MAX_SIDE = 256;

  /** Evita "bomba" de descompressão: dimensões lidas do cabeçalho antes de decodificar. */
  static final int MAX_SOURCE_SIDE = 6000;

  public enum Kind {
    JPEG,
    PNG,
    WEBP
  }

  private ProfilePhotoProcessor() {}

  /** Assinatura do arquivo (magic bytes). */
  public static Kind detect(byte[] data) {
    if (data.length >= 3
        && (data[0] & 0xFF) == 0xFF
        && (data[1] & 0xFF) == 0xD8
        && (data[2] & 0xFF) == 0xFF) {
      return Kind.JPEG;
    }
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    if (data.length >= 8 && startsWith(data, png, 0)) {
      return Kind.PNG;
    }
    if (data.length >= 12
        && startsWith(data, "RIFF".getBytes(), 0)
        && startsWith(data, "WEBP".getBytes(), 8)) {
      return Kind.WEBP;
    }
    return null;
  }

  /** Valida e regrava: devolve um PNG novo, sem metadados, com no máximo 256 px de lado. */
  public static byte[] process(byte[] data) {
    if (data == null || data.length == 0 || data.length > MAX_BYTES) {
      throw ApiException.invalidField("photo", "must be up to 2 MB");
    }
    Kind kind = detect(data);
    if (kind == null) {
      throw unsupported("must be a JPEG, PNG or WebP image");
    }
    if (kind == Kind.WEBP) {
      throw unsupported("WebP is not supported yet");
    }
    BufferedImage source = decode(data);
    int w = source.getWidth();
    int h = source.getHeight();
    double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
    int tw = Math.max(1, (int) Math.round(w * scale));
    int th = Math.max(1, (int) Math.round(h * scale));
    BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(source, 0, 0, tw, th, null);
    } finally {
      g.dispose();
    }
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      ImageIO.write(out, "png", bytes);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw unsupported("could not process the image");
    }
  }

  private static BufferedImage decode(byte[] data) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw unsupported("could not read the image");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        if (reader.getWidth(0) > MAX_SOURCE_SIDE || reader.getHeight(0) > MAX_SOURCE_SIDE) {
          throw ApiException.invalidField("photo", "is too large");
        }
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw unsupported("could not read the image");
    }
  }

  private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
    for (int i = 0; i < prefix.length; i++) {
      if (data[offset + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static ApiException unsupported(String message) {
    return new ApiException(ErrorCode.UNSUPPORTED_IMAGE, message);
  }
}
