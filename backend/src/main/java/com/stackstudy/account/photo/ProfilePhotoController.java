package com.stackstudy.account.photo;

import com.stackstudy.auth.SignupProperties;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Foto de perfil. {@code PROFILE_PHOTO_ENABLED=false} (padrão): 404, e o avatar são as iniciais.
 * Ligada, exige um {@link ProfilePhotoStorage}; sem ele o boot falha (não inventamos
 * armazenamento).
 */
@RestController
@RequestMapping("/api/me/photo")
@Tag(name = "Account")
public class ProfilePhotoController {

  private final SignupProperties properties;
  private final ObjectProvider<ProfilePhotoStorage> storage;
  private final CurrentUser currentUser;

  public ProfilePhotoController(
      SignupProperties properties,
      ObjectProvider<ProfilePhotoStorage> storage,
      CurrentUser currentUser) {
    this.properties = properties;
    this.storage = storage;
    this.currentUser = currentUser;
    if (properties.profilePhotoEnabled() && storage.getIfAvailable() == null) {
      throw new IllegalStateException(
          "PROFILE_PHOTO_ENABLED=true needs a ProfilePhotoStorage bean (none configured)");
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Upload a profile photo (JPEG/PNG up to 2 MB; re-encoded without metadata)")
  public void upload(@RequestParam("photo") MultipartFile photo) throws IOException {
    if (!properties.profilePhotoEnabled()) {
      throw ApiException.notFound("Endpoint");
    }
    byte[] png = ProfilePhotoProcessor.process(photo.getBytes());
    storage.getObject().save(currentUser.id(), png);
  }
}
