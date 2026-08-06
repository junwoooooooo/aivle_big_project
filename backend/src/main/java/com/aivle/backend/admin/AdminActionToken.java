package com.aivle.backend.admin;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "admin_action_tokens") @Getter @NoArgsConstructor
public class AdminActionToken {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long actorUserId;
 @Column(nullable=false,length=60) private String purpose;
 @Column(nullable=false,unique=true,length=64) private String tokenHash;
 @Column(nullable=false) private LocalDateTime expiresAt;
 private LocalDateTime usedAt;
 @Column(nullable=false) private Long securityVersion;
 @Column(nullable=false) private LocalDateTime createdAt;
 private AdminActionToken(Long actorUserId,String purpose,String tokenHash,LocalDateTime expiresAt,Long securityVersion,LocalDateTime createdAt){this.actorUserId=actorUserId;this.purpose=purpose;this.tokenHash=tokenHash;this.expiresAt=expiresAt;this.securityVersion=securityVersion;this.createdAt=createdAt;}
 public static AdminActionToken issue(Long actor,String purpose,String hash,LocalDateTime expiresAt,Long securityVersion,LocalDateTime now){return new AdminActionToken(actor,purpose,hash,expiresAt,securityVersion,now);}
 public boolean usableAt(LocalDateTime now,Long actor,String expectedPurpose,Long version){return usedAt==null&&expiresAt.isAfter(now)&&actorUserId.equals(actor)&&purpose.equals(expectedPurpose)&&securityVersion.equals(version);}
 public void consume(LocalDateTime now){usedAt=now;}
}
