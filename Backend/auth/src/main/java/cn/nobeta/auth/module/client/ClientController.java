package cn.nobeta.auth.module.client;

import cn.nobeta.auth.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/clients")
public class ClientController {
    private final ClientService clients;
    public ClientController(ClientService clients) { this.clients = clients; }

    @PostMapping
    ResponseEntity<ClientService.SecretResponse> create(@Valid @RequestBody ClientRequest request) {
        var result = clients.create(request);
        return ResponseEntity.created(java.net.URI.create("./clients/" + result.client().id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping
    PageResponse<ClientView> page(@RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return clients.page(query, enabled, page, size);
    }

    @GetMapping("/{id}") ClientView detail(@PathVariable @Size(max = 100) String id) {
        return clients.detail(id);
    }
    @PutMapping("/{id}") ClientView update(@PathVariable @Size(max = 100) String id,
            @Valid @RequestBody ClientRequest request) {
        return clients.update(id, request);
    }
    public record StatusRequest(@NotNull Boolean enabled) {}
    @PutMapping("/{id}/status") ClientView status(@PathVariable @Size(max = 100) String id,
            @Valid @RequestBody StatusRequest request) {
        return clients.status(id, request.enabled());
    }
    @PostMapping("/{id}/secret/reset") ResponseEntity<ClientService.SecretResponse> reset(
            @PathVariable @Size(max = 100) String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(clients.resetSecret(id));
    }
}
