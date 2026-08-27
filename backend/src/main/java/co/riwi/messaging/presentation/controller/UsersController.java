package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.command.ModifyUserCommand;
import co.riwi.messaging.application.usecase.ModifyUserUseCase;
import co.riwi.messaging.application.usecase.QueryUsersUseCase;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.presentation.dto.ModifyUserRequest;
import co.riwi.messaging.presentation.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** OBJ-01 (directory search) and OBJ-02 (admin role change / logical deactivation). */
@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final QueryUsersUseCase queryUsersUseCase;
    private final ModifyUserUseCase modifyUserUseCase;

    public UsersController(QueryUsersUseCase queryUsersUseCase, ModifyUserUseCase modifyUserUseCase) {
        this.queryUsersUseCase = queryUsersUseCase;
        this.modifyUserUseCase = modifyUserUseCase;
    }

    @GetMapping
    public Map<String, Object> search(
            @CurrentActor AuthenticatedActor actor,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<UserSummaryResponse> items = queryUsersUseCase.execute(actor.userId(), query, limit, null, null)
                .stream().map(UserSummaryResponse::from).toList();
        return Map.of("items", items);
    }

    @PatchMapping("/{userId}")
    public void modify(@CurrentActor AuthenticatedActor actor, @PathVariable String userId,
                        @Valid @RequestBody ModifyUserRequest request) {
        modifyUserUseCase.execute(actor.userId(), UserId.of(userId), new ModifyUserCommand(request.role(), request.deactivate()));
    }
}
