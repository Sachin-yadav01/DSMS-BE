package com.dsms.users;

import com.dsms.common.api.PageResponse;
import com.dsms.common.domain.UserStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserService.UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return users.list(page, size);
    }

    @GetMapping("/{id}")
    public UserService.UserResponse get(@PathVariable String id) {
        return users.get(id);
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public UserService.UserResponse invite(@Valid @RequestBody UserService.CreateUserRequest request) {
        return users.invite(request);
    }

    @PutMapping("/{id}")
    public UserService.UserResponse update(@PathVariable String id, @Valid @RequestBody UserService.UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PostMapping("/{id}/role")
    public UserService.UserResponse role(@PathVariable String id, @Valid @RequestBody UserService.ChangeRoleRequest request) {
        return users.changeRole(id, request);
    }

    @PostMapping("/{id}/activate")
    public UserService.UserResponse activate(@PathVariable String id) {
        return users.setStatus(id, UserStatus.ACTIVE);
    }

    @PostMapping("/{id}/suspend")
    public UserService.UserResponse suspend(@PathVariable String id) {
        return users.setStatus(id, UserStatus.SUSPENDED);
    }
}
