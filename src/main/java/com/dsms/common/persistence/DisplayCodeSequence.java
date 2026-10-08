package com.dsms.common.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "display_code_sequence")
public class DisplayCodeSequence {
    @Id
    private String prefix;
    private long nextValue;
}
