package com.hnh.example.transaction_example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "authority")
@Getter
@Setter
@NoArgsConstructor
public class Authority {

    @Id
    @Column(name = "name", nullable = false)
    private String name;
}
