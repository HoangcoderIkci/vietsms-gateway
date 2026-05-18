package com.hoangcoder.vietsms.sms;

import com.hoangcoder.vietsms.common.NotFoundException;
import com.hoangcoder.vietsms.security.ApiKey;
import com.hoangcoder.vietsms.sms.dto.PageResponse;
import com.hoangcoder.vietsms.sms.dto.SendSmsRequest;
import com.hoangcoder.vietsms.sms.dto.SmsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/sms")
@RequiredArgsConstructor
@Tag(name = "SMS", description = "Send and inspect SMS messages")
public class SmsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SmsService smsService;

    @PostMapping("/send")
    @Operation(summary = "Enqueue an SMS for delivery",
            description = "Returns 202 with the new message in QUEUED state. " +
                    "If `clientMessageId` was already used by this API key, the original message is returned unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Message accepted and queued"),
            @ApiResponse(responseCode = "400", description = "Validation error (invalid phone, message too long)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded (10 SMS/min/key by default)")
    })
    public ResponseEntity<SmsResponse> send(
            @AuthenticationPrincipal ApiKey key,
            @Valid @RequestBody SendSmsRequest request) {
        SmsMessage saved = smsService.send(key.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(SmsResponse.from(saved));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single SMS message by id, scoped to the caller's API key")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message found"),
            @ApiResponse(responseCode = "404", description = "No message with this id for this API key"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid x-api-key header")
    })
    public SmsResponse get(@AuthenticationPrincipal ApiKey key, @PathVariable Long id) {
        return smsService.getById(key.getId(), id)
                .map(SmsResponse::from)
                .orElseThrow(() -> new NotFoundException("SMS message " + id + " not found"));
    }

    @GetMapping
    @Operation(summary = "List SMS messages for the caller's API key (paginated, newest first)")
    public PageResponse<SmsResponse> list(
            @AuthenticationPrincipal ApiKey key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SmsStatus status) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(smsService.list(key.getId(), status, pageable), SmsResponse::from);
    }
}
