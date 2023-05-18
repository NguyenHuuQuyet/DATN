package com.quyet.application.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "cart")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "guid")
    private String guid;

    @Column(name = "user_id")
    private long user;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "size")
    private int size;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "created_at")
    @CreatedDate
    private Timestamp createdAt;

    @Column(name = "modified_at")
    @LastModifiedDate
    private Timestamp modifiedAt;
}
