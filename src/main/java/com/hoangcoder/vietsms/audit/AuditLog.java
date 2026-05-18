package com.hoangcoder.vietsms.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(nullable = false, length = 128)
    private String endpoint;

    @Column(nullable = false, length = 8)
    private String method;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "phone_masked", length = 16)
    private String phoneMasked;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
