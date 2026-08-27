package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.command.UpdateProfileCommand;
import co.riwi.messaging.application.usecase.GetProfileUseCase;
import co.riwi.messaging.application.usecase.UpdateProfileUseCase;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.presentation.dto.UpdateProfileRequest;
import co.riwi.messaging.presentation.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** GET/PATCH /me — the actor comes only from {@link CurrentActor} (AUTH-09). */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final GetProfileUseCase getProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;

    public ProfileController(GetProfileUseCase getProfileUseCase, UpdateProfileUseCase updateProfileUseCase) {
        this.getProfileUseCase = getProfileUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
    }

    @GetMapping
    public UserProfileResponse me(@CurrentActor AuthenticatedActor actor) {
        return UserProfileResponse.from(getProfileUseCase.execute(actor.userId()));
    }

    @PatchMapping
    public UserProfileResponse update(@CurrentActor AuthenticatedActor actor, @Valid @RequestBody UpdateProfileRequest request) {
        return UserProfileResponse.from(updateProfileUseCase.execute(
                actor.userId(), new UpdateProfileCommand(request.displayName(), request.locale(), request.avatarUrl())));
    }
}
